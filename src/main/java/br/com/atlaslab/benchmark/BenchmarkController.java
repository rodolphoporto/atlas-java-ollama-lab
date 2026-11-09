package br.com.atlaslab.benchmark;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {
    private final BenchmarkSuite suite;
    private final BenchmarkService service;

    public BenchmarkController(BenchmarkSuite suite, BenchmarkService service) {
        this.suite = suite;
        this.service = service;
    }

    @GetMapping("/prompts")
    public List<BenchmarkSuite.Prompt> prompts() { return suite.prompts(); }

    @GetMapping("/results")
    public List<BenchmarkService.ResultView> results() { return service.results(); }

    @GetMapping("/summary")
    public BenchmarkService.SummaryResponse summary() { return service.summary(); }

    @PostMapping("/runs")
    public BenchmarkService.ResultView run(@Valid @RequestBody RunRequest request) {
        return service.run(request.model(), request.promptId(), request.iteration());
    }

    @PatchMapping("/results/{id}/evaluation")
    public BenchmarkService.ResultView evaluate(@PathVariable UUID id,
                                                 @Valid @RequestBody EvaluationRequest request) {
        return service.evaluate(id, request.qualityScore(), request.notes());
    }

    public record RunRequest(@NotBlank @Size(max = 100) String model,
                             @NotBlank @Size(max = 50) String promptId,
                             @Min(1) @Max(3) int iteration) {}

    public record EvaluationRequest(@Min(1) @Max(5) int qualityScore,
                                    @Size(max = 1000) String notes) {}
}
