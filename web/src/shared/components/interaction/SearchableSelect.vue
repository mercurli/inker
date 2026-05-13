<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

interface Props {
  modelValue: string
  options: string[]
  inputId?: string
  placeholder?: string
  allowCustom?: boolean
  disabled?: boolean
  showAllOptionsOnOpen?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择',
  allowCustom: true,
  disabled: false,
  showAllOptionsOnOpen: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

const listId = `searchable-select-list-${Math.random().toString(36).slice(2, 10)}`

const rootRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLInputElement | null>(null)
const inputValue = ref(props.modelValue)
const isOpen = ref(false)
const highlightedIndex = ref(-1)
const hasTypedSinceOpen = ref(false)

const normalizedOptions = computed(() => {
  const seen = new Set<string>()

  return props.options
    .map((option) => option.trim())
    .filter((option) => option.length > 0)
    .filter((option) => {
      if (seen.has(option)) {
        return false
      }
      seen.add(option)
      return true
    })
})

const filteredOptions = computed(() => {
  const query = inputValue.value.trim().toLowerCase()

  if (!query) {
    return normalizedOptions.value
  }

  return normalizedOptions.value.filter((option) =>
    option.toLowerCase().includes(query)
  )
})

const visibleOptions = computed(() => {
  if (props.showAllOptionsOnOpen && isOpen.value && !hasTypedSinceOpen.value) {
    return normalizedOptions.value
  }

  return filteredOptions.value
})

const activeDescendantId = computed(() => {
  if (!isOpen.value || highlightedIndex.value < 0 || highlightedIndex.value >= visibleOptions.value.length) {
    return undefined
  }

  return optionId(highlightedIndex.value)
})

watch(
  () => props.modelValue,
  (value) => {
    if (value !== inputValue.value) {
      inputValue.value = value
    }
  }
)

watch(
  visibleOptions,
  (options) => {
    if (options.length === 0) {
      highlightedIndex.value = -1
      return
    }

    if (highlightedIndex.value >= options.length) {
      highlightedIndex.value = 0
    }
  },
  { immediate: true }
)

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) {
      closeDropdown()
    }
  }
)

function optionId(index: number) {
  return `${listId}-option-${index}`
}

function emitModelValue(value: string) {
  if (props.modelValue !== value) {
    emit('update:modelValue', value)
  }
}

function setInputValue(value: string) {
  inputValue.value = value
  emitModelValue(value)
}

function openDropdown() {
  if (props.disabled) {
    return
  }

  isOpen.value = true
  syncHighlightedIndex()
}

function closeDropdown() {
  isOpen.value = false
  highlightedIndex.value = -1
  hasTypedSinceOpen.value = false
}

function syncHighlightedIndex() {
  const options = visibleOptions.value

  if (options.length === 0) {
    highlightedIndex.value = -1
    return
  }

  const selectedIndex = options.findIndex((option) => option === inputValue.value)
  highlightedIndex.value = selectedIndex >= 0 ? selectedIndex : 0
  scrollOptionIntoView(highlightedIndex.value)
}

function scrollOptionIntoView(index: number) {
  void nextTick(() => {
    const optionElement = rootRef.value?.querySelector<HTMLElement>(`#${optionId(index)}`)
    optionElement?.scrollIntoView({ block: 'nearest' })
  })
}

function moveHighlight(step: 1 | -1) {
  const options = visibleOptions.value

  if (options.length === 0) {
    highlightedIndex.value = -1
    return
  }

  if (highlightedIndex.value < 0) {
    highlightedIndex.value = step === 1 ? 0 : options.length - 1
  } else {
    highlightedIndex.value = (highlightedIndex.value + step + options.length) % options.length
  }

  scrollOptionIntoView(highlightedIndex.value)
}

function selectOption(option: string) {
  setInputValue(option)
  emit('change', option)
  closeDropdown()
}

function clearValue() {
  setInputValue('')
  emit('change', '')
  closeDropdown()
  inputRef.value?.focus()
}

function normalizeCommittedValue(rawValue: string) {
  const trimmed = rawValue.trim()

  if (!trimmed) {
    return ''
  }

  const matched = normalizedOptions.value.find(
    (option) => option.toLowerCase() === trimmed.toLowerCase()
  )

  if (matched) {
    return matched
  }

  if (props.allowCustom) {
    return trimmed
  }

  return props.modelValue
}

function commitValue() {
  const nextValue = normalizeCommittedValue(inputValue.value)
  setInputValue(nextValue)
  emit('change', nextValue)
  closeDropdown()
}

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  hasTypedSinceOpen.value = true
  setInputValue(target.value)
  openDropdown()
}

function onInputKeydown(event: KeyboardEvent) {
  if (props.disabled) {
    return
  }

  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!isOpen.value) {
      openDropdown()
      return
    }

    moveHighlight(1)
    return
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!isOpen.value) {
      openDropdown()
      return
    }

    moveHighlight(-1)
    return
  }

  if (event.key === 'Escape') {
    event.preventDefault()
    closeDropdown()
    return
  }

  if (event.key === 'Enter') {
    event.preventDefault()

    if (
      isOpen.value &&
      highlightedIndex.value >= 0 &&
      highlightedIndex.value < visibleOptions.value.length
    ) {
      selectOption(visibleOptions.value[highlightedIndex.value] ?? '')
      return
    }

    commitValue()
  }
}

function onRootFocusOut(event: FocusEvent) {
  const nextFocused = event.relatedTarget as Node | null

  if (nextFocused && rootRef.value?.contains(nextFocused)) {
    return
  }

  commitValue()
}
</script>

<template>
  <div ref="rootRef" class="searchable-select" @focusout="onRootFocusOut">
    <div class="searchable-select__control">
      <input
        ref="inputRef"
        :id="inputId"
        class="field-control searchable-select__input"
        type="text"
        :value="inputValue"
        :placeholder="placeholder"
        :disabled="disabled"
        role="combobox"
        aria-autocomplete="list"
        :aria-expanded="isOpen"
        :aria-controls="listId"
        :aria-activedescendant="activeDescendantId"
        @focus="openDropdown"
        @click="openDropdown"
        @input="onInput"
        @keydown="onInputKeydown"
      />
      <button
        v-if="!disabled && inputValue"
        class="searchable-select__clear"
        type="button"
        aria-label="清空筛选"
        @mousedown.prevent
        @click="clearValue"
      >
        ×
      </button>
      <span class="searchable-select__arrow" :class="{ 'searchable-select__arrow--open': isOpen }" aria-hidden="true">
        ▾
      </span>
    </div>

    <ul v-show="isOpen" :id="listId" class="searchable-select__menu" role="listbox">
      <li v-if="visibleOptions.length === 0" class="searchable-select__empty">
        无匹配项
      </li>
      <li
        v-for="(option, index) in visibleOptions"
        :id="optionId(index)"
        :key="option"
        class="searchable-select__option"
        :class="{
          'searchable-select__option--highlighted': highlightedIndex === index,
          'searchable-select__option--selected': option === modelValue
        }"
        role="option"
        :aria-selected="option === modelValue"
        @mousedown.prevent
        @mousemove="highlightedIndex = index"
        @click="selectOption(option)"
      >
        {{ option }}
      </li>
    </ul>
  </div>
</template>
