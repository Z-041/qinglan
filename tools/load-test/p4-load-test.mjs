/**
 * P4 压测脚本（本地冒烟级）。
 *
 * 目的：给「读多写少」的公开只读接口一个可复现的并发基线，用来发现明显退化，而不是
 * 产出可对外承诺的容量数字——本机同时跑着被测应用与压测进程，且项目未定义 SLA。
 *
 * 用法：
 *   node tools/load-test/p4-load-test.mjs [--seconds 20] [--concurrency 20] [--base http://localhost:8080]
 *
 * 认证接口需要令牌时通过环境变量给：
 *   $env:QL_TOKEN='<access token>'   （PowerShell）
 *
 * 说明：不压 POST /api/announcements/{id}/read —— 该接口按设备限流（30 次/分钟），
 * 并发打它只会批量拿 429，测不出容量。
 */

const args = new Map()
for (let i = 2; i < process.argv.length; i += 2) {
  args.set(process.argv[i].replace(/^--/, ''), process.argv[i + 1])
}

const seconds = Number(args.get('seconds') ?? 20)
const concurrency = Number(args.get('concurrency') ?? 20)
const base = args.get('base') ?? 'http://localhost:8080'

/** 被测场景：名称 → 请求构造 */
const scenarios = [
  { name: 'GET /api/posts', path: '/api/posts', auth: false },
  { name: 'GET /api/announcements/pending?deviceId=', path: '/api/announcements/pending?deviceId=loadtest-device', auth: false },
  { name: 'GET /api/notifications', path: '/api/notifications', auth: true },
  { name: 'GET /api/notifications/unread-count', path: '/api/notifications/unread-count', auth: true },
]

function percentile(sorted, p) {
  if (sorted.length === 0) return 0
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return sorted[index]
}

async function runScenario(scenario, token) {
  const samples = []
  const statuses = new Map()
  const deadline = Date.now() + seconds * 1000

  const worker = async () => {
    while (Date.now() < deadline) {
      const started = process.hrtime.bigint()
      let status = 0
      try {
        const headers = scenario.auth && token ? { Authorization: `Bearer ${token}` } : {}
        const response = await fetch(base + scenario.path, { headers })
        status = response.status
        await response.arrayBuffer()
      } catch {
        status = -1
      }
      const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6
      samples.push(elapsedMs)
      statuses.set(status, (statuses.get(status) ?? 0) + 1)
    }
  }

  await Promise.all(Array.from({ length: concurrency }, worker))

  samples.sort((a, b) => a - b)
  const ok = statuses.get(200) ?? 0
  const total = samples.length
  return {
    name: scenario.name,
    total,
    rps: total / seconds,
    ok,
    notOk: total - ok,
    okRate: total === 0 ? 0 : (ok / total) * 100,
    p50: percentile(samples, 50),
    p95: percentile(samples, 95),
    p99: percentile(samples, 99),
    max: samples.length === 0 ? 0 : samples[samples.length - 1],
    statuses: [...statuses.entries()].sort((a, b) => b[1] - a[1]).map(([s, c]) => `${s}×${c}`).join(' '),
  }
}

const token = process.env.QL_TOKEN ?? ''
if (!token) {
  console.log('未提供 QL_TOKEN，认证场景会被跳过（401 不计入通过率）')
}

console.log(`base=${base} seconds=${seconds} concurrency=${concurrency}`)
console.log('')
console.log('scenario                                 reqs    rps   ok%    p50     p95     p99     max   statuses')

const report = []
for (const scenario of scenarios) {
  const result = await runScenario(scenario, token)
  report.push(result)
  const pad = (value, width) => String(value).padEnd(width)
  console.log(
    pad(result.name, 40) +
      pad(result.total, 7) +
      pad(result.rps.toFixed(1), 6) +
      pad(result.okRate.toFixed(1), 7) +
      pad(result.p50.toFixed(1), 7) +
      pad(result.p95.toFixed(1), 8) +
      pad(result.p99.toFixed(1), 8) +
      pad(result.max.toFixed(1), 8) +
      '  ' +
      result.statuses,
  )
}

console.log('')
console.log('注意：被测应用与压测进程同机运行，且本项目未定义 SLA，以上数字只用于发现明显退化，')
console.log('     不能作为对外容量承诺。')
