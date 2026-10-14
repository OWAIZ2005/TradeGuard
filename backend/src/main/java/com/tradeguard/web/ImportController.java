package com.tradeguard.web;

import com.tradeguard.service.ImportQueryService;
import com.tradeguard.service.ImportService;
import com.tradeguard.web.dto.BatchDetailDto;
import com.tradeguard.web.dto.ErrorDto;
import com.tradeguard.web.dto.ImportBatchDto;
import com.tradeguard.web.dto.PageResponse;
import com.tradeguard.web.dto.SummaryDto;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;
    private final ImportQueryService queries;

    public ImportController(ImportService importService, ImportQueryService queries) {
        this.importService = importService;
        this.queries = queries;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchDetailDto> upload(@RequestParam("file") MultipartFile file) {
        BatchDetailDto result = importService.importCsv(file.getOriginalFilename(), file.getSize(),
                file.getContentType(), file::getInputStream);
        return ResponseEntity.created(URI.create("/api/imports/" + result.batch().id())).body(result);
    }

    @GetMapping
    public PageResponse<ImportBatchDto> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String status) {
        return queries.list(Paging.of(page, size), status);
    }

    @GetMapping("/{id}")
    public BatchDetailDto get(@PathVariable long id) {
        return queries.detail(id);
    }

    @GetMapping("/{id}/errors")
    public PageResponse<ErrorDto> errors(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size,
                                         @RequestParam(required = false) String code) {
        return queries.errors(id, Paging.of(page, size), code);
    }

    @GetMapping("/{id}/summary")
    public SummaryDto summary(@PathVariable long id) {
        return queries.detail(id).summary();
    }
}
