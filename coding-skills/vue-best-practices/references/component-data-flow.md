Component Data Flow Best Practices

impact
HIGH

impactDescription
Clear data flow between components prevents state bugs, stale UI, and brittle coupling

type
best-practice

# tags

## vue3

## props

## emits

## v-model

## provide-inject

## data-flow

## typescript

## Component Data Flow Best Practices

Impact: HIGH - Vue components stay reliable when data flow is explicit: props go down, events go up, v-model handles two-way bindings, and provide/inject supports cross-tree dependencies. Blurring these boundaries leads to stale state, hidden coupling, and hard-to-debug UI.

The main principle of data flow in Vue.js is Props Down / Events Up. This is the most maintainable default, and one-way flow scales well.

## Task List

## Treat props as read-only inputs

Use props/emit for component communication; reserve refs for imperative actions

When refs are required for imperative APIs, type them with template refs

## Emit events instead of mutating parent state directly

Use defineModel for v-model in modern Vue (3.4+)

## Handle v-model modifiers deliberately in child components

Use symbols for provide/inject keys to avoid props drilling (over ~3 layers)

Keep mutations in the provider or expose explicit actions

In TypeScript projects, prefer type-based defineProps, defineEmits, and InjectionKey

## Props: One-Way Data Down

Props are inputs. Do not mutate them in the child.

BAD:

GOOD:

If state needs to change, emit an event, use v-model or create a local copy.

## Prefer props/emit over component refs

BAD:

## Submit

GOOD:

## Type component refs when imperative access is required

Prefer props/emits by default. When a parent must call an exposed child method, type the ref explicitly and expose only the intended API from the child with defineExpose.

BAD:

GOOD:

## Emits: Explicit Events Up

Component events do not bubble. If a parent needs to know about an event, re-emit it explicitly.

BAD:

GOOD:

Event naming: use kebab-case in templates and camelCase in script:

## v-model: Predictable Two-Way Bindings

Use defineModel by default for component bindings and emit updates on input. Only use the modelValue + update:modelValue pattern if you are on Vue < 3.4.

BAD:

GOOD (Vue 3.4+):

GOOD (Vue < 3.4):

If you need the updated value immediately after a change, use the input event value or nextTick in the parent.

## Provide/Inject: Shared Context Without Prop Drilling

Use provide/inject for cross-tree state, but keep mutations centralized in the provider and expose explicit actions.

BAD:

// Provider.vue
provide('theme', reactive({ dark: false }))

// Consumer.vue
const theme = inject('theme')
// Mutating shared state from any depth becomes hard to track
theme.dark = true

GOOD:

// Provider.vue
const theme = reactive({ dark: false })
const toggleTheme = () => { theme.dark = !theme.dark }

provide(themeKey, readonly(theme))
provide(themeActionsKey, { toggleTheme })

// Consumer.vue
const theme = inject(themeKey)
const { toggleTheme } = inject(themeActionsKey)

Use symbols for keys to avoid collisions in large apps:

export const themeKey = Symbol('theme')
export const themeActionsKey = Symbol('theme-actions')

## Use TypeScript Contracts for Public Component APIs

In TypeScript projects, type component boundaries directly with defineProps, defineEmits, and InjectionKey so invalid payloads and mismatched injections fail at compile time.

BAD:

GOOD:
