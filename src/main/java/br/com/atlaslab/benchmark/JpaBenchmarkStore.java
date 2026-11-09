package br.com.atlaslab.benchmark;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaBenchmarkStore implements BenchmarkStore {
    private final BenchmarkResultRepository repository;

    JpaBenchmarkStore(BenchmarkResultRepository repository) { this.repository = repository; }

    public BenchmarkResult save(BenchmarkResult result) { return repository.save(result); }
    public List<BenchmarkResult> list() { return repository.findAllByOrderByCreatedAtDesc(); }
    public Optional<BenchmarkResult> find(UUID id) { return repository.findById(id); }
}
