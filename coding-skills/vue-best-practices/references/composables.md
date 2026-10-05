Composable Organization Patterns

impact
MEDIUM

impactDescription
Well-structured composables improve maintainability, reusability, and update performance

type
best-practice

# tags

## vue3

## composables

## composition-api

## code-organization

## api-design

## readonly

## utilities

## Composable Organization Patterns

Impact: MEDIUM - Treat composables as reusable, stateful building blocks and keep their code organized by feature concern. This keeps large components maintainable and prevents hard-to-debug mutation and API design issues.

## Task List

## Compose complex behavior from small, focused composables

Use options objects for composables with multiple optional parameters

Return readonly state when updates must flow through explicit actions

Keep pure utility functions as plain utilities, not composables

Organize composable and component code by feature concern, and extract composables when components grow

## Compose Composables from Smaller Primitives

BAD:

GOOD:

// composables/useEventListener.js
import { onMounted, onUnmounted, toValue } from 'vue'

export function useEventListener(target, event, callback) {
 onMounted(() => toValue(target).addEventListener(event, callback))
 onUnmounted(() => toValue(target).removeEventListener(event, callback))
}

// composables/useMouse.js
import { ref } from 'vue'
import { useEventListener } from './useEventListener'

export function useMouse() {
 const x = ref(0)
 const y = ref(0)

 useEventListener(window, 'mousemove', (e) => {
 x.value = e.pageX
 y.value = e.pageY
 })

 return { x, y }
}

// composables/useMouseInElement.js
import { computed } from 'vue'
import { useMouse } from './useMouse'

export function useMouseInElement(elementRef) {
 const { x, y } = useMouse()

 const isOutside = computed(() => {
 if (!elementRef.value) return true
 const rect = elementRef.value.getBoundingClientRect()
 return x.value < rect.left || x.value > rect.right ||
 y.value < rect.top || y.value > rect.bottom
 })

 return { x, y, isOutside }
}

## Use Options Object Pattern for Composable Parameters

BAD:

export function useFetch(url, method, headers, timeout, retries, immediate) {
 // hard to read and easy to misorder
}

useFetch('/api/users', 'GET', null, 5000, 3, true)

GOOD:

export function useFetch(url, options = {}) {
 const {
 method = 'GET',
 headers = {},
 timeout = 30000,
 retries = 0,
 immediate = true
 } = options

 // implementation
 return { method, headers, timeout, retries, immediate }
}

useFetch('/api/users', {
 method: 'POST',
 timeout: 5000,
 retries: 3
})

interface UseCounterOptions {
 initial?: number
 min?: number
 max?: number
 step?: number
}

export function useCounter(options: UseCounterOptions = {}) {
 const { initial = 0, min = -Infinity, max = Infinity, step = 1 } = options
 // implementation
}

## Return Readonly State with Explicit Actions

BAD:

export function useCart() {
 const items = ref([])
 const total = computed(() => items.value.reduce((sum, item) => sum + item.price, 0))
 return { items, total } // any consumer can mutate directly
}

const { items } = useCart()
items.value.push({ id: 1, price: 10 })

GOOD:

## import { ref, computed, readonly } from 'vue'

export function useCart() {
 const _items = ref([])

 const total = computed(() =>
 _items.value.reduce((sum, item) => sum + item.price * item.quantity, 0)
 )

 function addItem(product, quantity = 1) {
 const existing = _items.value.find(item => item.id === product.id)
 if (existing) {
 existing.quantity += quantity
 return
 }
 _items.value.push({ ...product, quantity })
 }

 function removeItem(productId) {
 _items.value = _items.value.filter(item => item.id !== productId)
 }

 return {
 items: readonly(_items),
 total,
 addItem,
 removeItem
 }
}

## Keep Utilities as Utilities

BAD:

export function useFormatters() {
 const formatDate = (date) => new Intl.DateTimeFormat('en-US').format(date)
 const formatCurrency = (amount) =>
 new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount)
 return { formatDate, formatCurrency }
}

const { formatDate } = useFormatters()

GOOD:

// utils/formatters.js
export function formatDate(date) {
 return new Intl.DateTimeFormat('en-US').format(date)
}

export function formatCurrency(amount) {
 return new Intl.NumberFormat('en-US', {
 style: 'currency',
 currency: 'USD'
 }).format(amount)
}

// composables/useInvoiceSummary.js
import { computed } from 'vue'
import { formatCurrency } from '@/utils/formatters'

export function useInvoiceSummary(invoiceRef) {
 const totalLabel = computed(() => formatCurrency(invoiceRef.value.total))
 return { totalLabel }
}

## Organize Composable and Component Code by Feature Concern

BAD:

GOOD:

// composables/useItems.js
import { ref, onMounted } from 'vue'

export function useItems() {
 const items = ref([])
 const loading = ref(false)

 async function fetchItems() {
 loading.value = true
 try {
 items.value = await api.getItems()
 } finally {
 loading.value = false
 }
 }

 onMounted(fetchItems)
 return { items, loading, fetchItems }
}
