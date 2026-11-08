# Performance Phase 17 — One lookup for the DEM column pair

The measured DEM density path now obtains the fade weight and extended height through one per-worker X/Z/fade-width cache lookup. The former production path performed separate ThreadLocal lookups for the weight and height caches on every vertical sample in the exterior transition ring. The combined bounded cache stores the exact same source weight and height values and only computes the height when the weight is positive.

The independent cached accessors remain available for existing tests and benchmarks. New raw-bit tests compare the combined pair against those independent accessors across the entire fade domain and eight parallel workers. A paired same-process benchmark measures the former two-cache path against the combined path with 64 vertical samples per horizontal column; it reports timings without imposing a noisy CI speed threshold.
