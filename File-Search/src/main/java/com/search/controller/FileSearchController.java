package com.search.controller;

import java.io.File;
import java.io.IOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.search.entity.ExcelWriter;
import com.search.entity.SearchRequest;
import com.search.exception.SearchException;
import com.search.service.FilesSearch;

@RestController
@RequestMapping("/FileSearch")

public class FileSearchController {
	@PostMapping("/search")
    public String searchFiles(@RequestBody SearchRequest request) throws IOException {
        File directory = new File(request.getDirectory());
        String searchString = request.getSearchString();
        ExcelWriter excelWriter = new ExcelWriter();
        try {
            FilesSearch.searchDirectory(directory, searchString, excelWriter);
            excelWriter.save();
            return "Search completed successfully.";
        } catch (SearchException e) {
            return e.getMessage();
        }
    }

}
