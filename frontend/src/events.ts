/** Bounded duplicate suppression; authoritative refetch remains the source of state. */
export class EventWindow {
  private readonly ids = new Set<string>();
  constructor(private readonly capacity = 512) {}
  accept(id: string): boolean {
    if (this.ids.has(id)) return false;
    this.ids.add(id);
    if (this.ids.size > this.capacity) this.ids.delete(this.ids.values().next().value!);
    return true;
  }
  clear() { this.ids.clear(); }
}
