package com.graphchart.graphapi.api;

import com.graphchart.graphapi.service.HierarchyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GraphController {
    private final HierarchyService hierarchyService;

    public GraphController(HierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    @GetMapping("/{type}")
    public List<EntityResponse> list(@PathVariable String type) {
        return hierarchyService.list(type);
    }

    @GetMapping("/{type}/{id}")
    public EntityResponse get(@PathVariable String type, @PathVariable UUID id) {
        return hierarchyService.get(type, id);
    }

    @PostMapping("/{type}")
    @ResponseStatus(HttpStatus.CREATED)
    public EntityResponse create(@PathVariable String type, @Valid @RequestBody EntityRequest request) {
        return hierarchyService.create(type, request);
    }

    @PutMapping("/{type}/{id}")
    public EntityResponse update(@PathVariable String type, @PathVariable UUID id,
                                 @Valid @RequestBody EntityRequest request) {
        return hierarchyService.update(type, id, request);
    }

    @DeleteMapping("/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String type, @PathVariable UUID id) {
        hierarchyService.delete(type, id);
    }

    @GetMapping("/graph/{type}/{id}")
    public List<String> graph(@PathVariable String type, @PathVariable UUID id) {
        return hierarchyService.graph(type, id);
    }
}
