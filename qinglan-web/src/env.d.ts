/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口基地址，默认 /api（开发期由 Vite 代理） */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
