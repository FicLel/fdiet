<script setup lang="ts"></script>

<template>
  <div class="logo">
    <svg
      width="20"
      height="20"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path d="M10 17.5c0-5 1.8-8.4 5.5-10.2" />
      <path d="M14.6 3.2c1.6 3.6.6 7.4-2.3 9.1-1.9 1.1-4 .6-4.9-1.1-.9-1.8 0-3.9 1.9-5 1.5-.9 3.4-.9 5.3-3z" />
      <path d="M3.4 8.6c2.6.3 4.4 2 4.7 4.4" />
    </svg>
    <span class="wordmark">fdiet</span>
  </div>
</template>

<style scoped>
.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--sage-700);
}

.wordmark {
  font-weight: 600;
  font-size: 14px;
  letter-spacing: -0.01em;
  color: var(--ink-strong);
}
</style>
