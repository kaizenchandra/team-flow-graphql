import {describe, expect, it} from 'vitest';
import {EventWindow} from './events';

describe('subscription event deduplication', () => {
    it('suppresses duplicates and bounds retained IDs', () => {
        const events = new EventWindow(2);
        expect(events.accept('one')).toBe(true);
        expect(events.accept('one')).toBe(false);
        expect(events.accept('two')).toBe(true);
        expect(events.accept('three')).toBe(true);
        expect(events.accept('two')).toBe(false);
        expect(events.accept('one')).toBe(true);
    });
    it('clears IDs when changing workspaces', () => {
        const events = new EventWindow();
        events.accept('event');
        events.clear();
        expect(events.accept('event')).toBe(true);
    });
});
