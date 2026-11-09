package br.com.atlaslab.benchmark;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BenchmarkResultRepository extends JpaRepository<BenchmarkResult, UUID> {
    List<BenchmarkResult> findAllByOrderByCreatedAtDesc();
}
