/** Join class names, dropping anything falsy. The only shared helper in the system. */
export function cx(...parts) {
  return parts.filter(Boolean).join(' ');
}
