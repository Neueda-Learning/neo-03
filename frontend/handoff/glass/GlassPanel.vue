<!-- Havn glass panel — Vue 3. Requires glass.css imported at app root.
  <GlassPanel>frost</GlassPanel>
  <GlassPanel accent="#4353C4" tag="aside">team-tinted (max one per desk)</GlassPanel> -->
<script setup>
import { computed } from 'vue';
const TEAM_ACCENTS = {
  cards: '#A6C918', lending: '#E8A020', deposits: '#17A67E', fraud: '#D6337E', kyc: '#2E86C9',
  disputes: '#D9482B', payments: '#12A3B4', treasury: '#4353C4', collections: '#4E9E3A', support: '#8A4FD0',
};
const props = defineProps({
  tag: { type: String, default: 'div' },
  accent: { type: String, default: null },
  team: { type: String, default: null },
});
const tint = computed(() => props.accent ?? (props.team ? TEAM_ACCENTS[props.team] : null));
</script>

<template>
  <component
    :is="tag"
    class="havn-glass"
    :class="{ 'havn-glass--team': tint }"
    :style="tint ? { '--team-accent': tint } : null"
  >
    <slot></slot>
  </component>
</template>
