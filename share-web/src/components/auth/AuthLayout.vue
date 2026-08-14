<script setup lang="ts">
defineProps<{ title: string; description: string; step?: string }>()
</script>

<template>
  <main class="auth-page page-shell">
    <section class="auth-intro" aria-labelledby="auth-title">
      <RouterLink class="brand" to="/" aria-label="Share 首页">
        <span class="brand-mark" aria-hidden="true">S</span>
        <span>Share</span>
      </RouterLink>
      <p v-if="step" class="step">{{ step }}</p>
      <h1 id="auth-title">{{ title }}</h1>
      <p class="description">{{ description }}</p>
    </section>
    <section class="auth-card">
      <slot />
    </section>
  </main>
</template>

<style scoped lang="scss">
.auth-page { display: grid; align-content: center; gap: 28px; max-width: 460px; margin: 0 auto; }
.auth-intro { padding: 4px; }
.brand { display: inline-flex; align-items: center; gap: 10px; min-height: 44px; color: var(--share-text); font-size: 20px; font-weight: 800; text-decoration: none; }
.brand-mark { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 12px; color: white; background: var(--share-primary); }
.step { margin: 26px 0 6px; color: var(--share-primary-dark); font-size: 14px; font-weight: 700; }
h1 { margin: 22px 0 6px; font-size: clamp(30px, 9vw, 42px); line-height: 1.2; letter-spacing: -0.04em; }
.step + h1 { margin-top: 0; }
.description { margin: 0; color: var(--share-muted); }
.auth-card { padding: 22px 18px; border: 1px solid var(--share-border); border-radius: var(--share-radius); background: var(--share-surface); box-shadow: var(--share-shadow); }
@media (min-width: 900px) {
  .auth-page { grid-template-columns: 1fr 440px; max-width: 980px; gap: 80px; }
  .auth-card { padding: 30px; }
}
</style>
