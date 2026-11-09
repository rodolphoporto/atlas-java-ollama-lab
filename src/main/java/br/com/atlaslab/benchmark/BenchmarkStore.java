package br.com.atlaslab.benchmark;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BenchmarkStore {
    BenchmarkResult save(BenchmarkResult result);
    List<BenchmarkResult> list();
    Optional<BenchmarkResult> find(UUID id);
}
