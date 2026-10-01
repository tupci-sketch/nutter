/** Types for the packet-table generator, so the drift test can import it. */
export declare const JAVA_SOURCE: string;
export declare const TS_TARGET: string;
export declare function parseJava(source: string): Array<{ name: string; value: string }>;
export declare function render(entries: Array<{ name: string; value: string }>): string;
export declare function generate(): string;
