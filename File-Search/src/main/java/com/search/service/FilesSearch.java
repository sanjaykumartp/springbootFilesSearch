package com.search.service;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import com.search.entity.ExcelWriter;
import com.search.entity.SearchResult;
import com.search.exception.SearchException;

public class FilesSearch {

	public static String searchDirectory(File directory, String searchString, ExcelWriter excelWriter) throws IOException {
	    StringBuilder resultBuilder = new StringBuilder();
//	    boolean searchStringFound = false; // added variable to check if search string is found in any file
	    
	    // check if directory exists
	    if (!directory.exists()) {
	        resultBuilder.append("Invalid directory path: " + directory.getPath());
	        throw new SearchException(resultBuilder.toString());
	    }
	    // check if directory is empty
	    File[] files = directory.listFiles();
	    if (files == null || files.length == 0) {
	        resultBuilder.append("No text, doc, docx or pdf files found in : " + directory.getPath());
	        throw new SearchException(resultBuilder.toString());
	    }
	    // check if search string is empty
	    if (searchString.trim().isEmpty()) {
	        resultBuilder.append("Please enter a search string.");
	        throw new SearchException(resultBuilder.toString());
	    }

	    for (File file : files) {
	        if (file.isDirectory()) {
	            resultBuilder.append(searchDirectory(file, searchString, excelWriter));
	        } else {
	             searchFile(file, searchString, excelWriter); // modified to return SearchResult object
//	            if (result != null) { // searchString found in file
//	                searchStringFound = true;
//	                excelWriter.addResult(result);
//	            }
	        }
	    }
//	    if (!searchStringFound) { // throw exception if search string is not found in any file
//	        resultBuilder.append(searchString + " this keyword is not present in any file");
//	        throw new SearchException(resultBuilder.toString());
//	    }
//	    resultBuilder.append("Search Completed ");
	    return resultBuilder.toString();
	}

	private static SearchResult  searchFile(File file, String searchString, ExcelWriter excelWriter) throws IOException {
		String fileExtension = FilenameUtils.getExtension(file.getName());
		SearchResult result = null;
		switch (fileExtension) {
		case "txt":
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				StringBuilder fileContent = new StringBuilder(); // store the file content
				String firstLine = br.readLine(); // read the first line of the file
				String name = ""; // initialize the name variable
				if (firstLine != null) {
					String[] words = firstLine.trim().split("\\s+"); // split the first line by spaces
					if (words.length > 0) {
						name = words[0]; // set the name to the first word of the first line
					}
				}
				while ((firstLine = br.readLine()) != null) {
					fileContent.append(firstLine).append("\n");
				}
				String[] orKeywords = searchString.split("\\|\\|");
				boolean containsOrKeywords = false;
				for (String orKeyword : orKeywords) {
					orKeyword = orKeyword.trim().toLowerCase();
					String[] andKeywords = orKeyword.split("\\+");
					boolean containsAndKeywords = true;
					for (String andKeyword : andKeywords) {
						andKeyword = andKeyword.trim().toLowerCase();
						if (!fileContent.toString().toLowerCase().contains(andKeyword)) {
							containsAndKeywords = false;
							break;
						}
					}
					if (containsAndKeywords) {
						containsOrKeywords = true;
						break;
					}
				}
				if (containsOrKeywords) {
					 result = new SearchResult(file.getName(), "", "", "", searchString, getResumeCreatedDate(file),getResumeModifiedDate(file));
					result.setName(name); // set the name in the search result

					// Extract emails and mobile numbers from the file content
					Pattern emailPattern = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
					Pattern mobilePattern = Pattern.compile("\\b\\d{10}\\b");

					Matcher emailMatcher = emailPattern.matcher(fileContent.toString());
					while (emailMatcher.find()) {
						result.setEmail(emailMatcher.group());
					}

					Matcher mobileMatcher = mobilePattern.matcher(fileContent.toString());
					while (mobileMatcher.find()) {
						result.setMobileNumber(mobileMatcher.group());
					}

					result.setFileName(file.getName());
					result.setSearch_criteria(searchString);
					result.setResumeCreatedDate(getResumeCreatedDate(file));
					result.setResumeModifiedDate(getResumeModifiedDate(file));
					excelWriter.addResult(result);
				
				}
			}
			break;
		case "doc":
		case "docx":
			try (FileInputStream fis = new FileInputStream(file);XWPFDocument document = new XWPFDocument(fis)) {
				XWPFWordExtractor extractor = new XWPFWordExtractor(document);
				String text = extractor.getText();
				String[] orKeywords = searchString.split("\\|\\|");
				boolean containsOrKeywords = false;
				for (String orKeyword : orKeywords) {//this loop for when AND and OR condition together or only OR condition is there
					orKeyword = orKeyword.trim().toLowerCase();
					String[] andKeywords = orKeyword.split("\\+");
					boolean containsAndKeywords = true;
					for (String andKeyword : andKeywords) {//this loop for only AND condition is there
						andKeyword = andKeyword.trim().toLowerCase();
						if (!text.toLowerCase().contains(andKeyword)) {
							containsAndKeywords = false;
							break;
						}
					}
					if (containsAndKeywords) {
						containsOrKeywords = true;
						break;
					}
				}
				if (containsOrKeywords) {
					 result = new SearchResult(file.getName(), "", "", "", searchString, getResumeCreatedDate(file),getResumeModifiedDate(file));
					result.setFileName(file.getName());
					result.setSearch_criteria(searchString);
					result.setResumeCreatedDate(getResumeCreatedDate(file));
					result.setResumeModifiedDate(getResumeModifiedDate(file));
					extractNameEmailMobile(text, result);
					excelWriter.addResult(result);
					
				}
			}
			break;
		case "pdf":
			try (PDDocument document = PDDocument.load(file)) {
				PDFTextStripper stripper = new PDFTextStripper();
				String text = stripper.getText(document);
				String[] orKeywords = searchString.split("\\|\\|");
				boolean containsOrKeywords = false;
				for (String orKeyword : orKeywords) {
					orKeyword = orKeyword.trim().toLowerCase();
					String[] andKeywords = orKeyword.split("\\+");
					boolean containsAndKeywords = true;
					for (String andKeyword : andKeywords) {
						andKeyword = andKeyword.trim().toLowerCase();
						if (!text.toLowerCase().contains(andKeyword)) {
							containsAndKeywords = false;
							break;
						}
					}
					if (containsAndKeywords) {
						containsOrKeywords = true;
						break;
					}
				}
				if (containsOrKeywords) {
					 result = new SearchResult(file.getName(), "", "", "", searchString, getResumeCreatedDate(file),getResumeModifiedDate(file));
					result.setFileName(file.getName());
					result.setSearch_criteria(searchString);
					result.setResumeCreatedDate(getResumeCreatedDate(file));
					result.setResumeModifiedDate(getResumeModifiedDate(file));
					extractNameEmailMobile(text, result);
					excelWriter.addResult(result);
				
				}
			}
			break;
		default:
			System.out.println("Unsupported file type: " + fileExtension);
			break;
		}
		return result;
	}

	/*
	 * Modified Resume date
	 */
	private static String getResumeModifiedDate(File file) throws IOException {
		Path filePath = file.toPath();
		FileTime fileTime = Files.getLastModifiedTime(filePath);
		LocalDateTime localDateTime = LocalDateTime.ofInstant(fileTime.toInstant(), ZoneId.systemDefault());
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		return localDateTime.format(formatter);
	}
	/*
	 * Created Resume date
	 */
	private static String getResumeCreatedDate(File file) throws IOException {
		Path filePath = file.toPath();
		BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
		LocalDateTime localDateTime = LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		return localDateTime.format(formatter);
	}

	private static void extractNameEmailMobile(String text, SearchResult result) {
		String[] lines = text.split("\\r?\\n");//split using regular expression "This is the first line.", "This is the second line."
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (line.toLowerCase().contains("@") && line.toLowerCase().contains(".com")) {
				String[] parts = line.split("\\s+");
				for (String part : parts) {
					if (part.contains("@") && part.contains(".com")) {
						String email = part.replaceAll("[^a-zA-Z0-9@.]+", "");
						if (email.toLowerCase().startsWith("email:-")) {
							email = email.substring(7);
						}
						result.setEmail(email);
					}
				}
			}else {
				Pattern pattern = Pattern.compile("(\\d[\\s-]?){10}");
				Matcher matcher = pattern.matcher(line);
				while (matcher.find()) {
					String mobileNumber = matcher.group(0).replaceAll("\\s|-", "");
					result.setMobileNumber(mobileNumber);
				}
			}
			if (i == 0) {
				String firstLine = line.trim();
				if (firstLine.matches("(?i)^\\w+\\s*Name\\s*:(.*)$")) {
					String[] parts = firstLine.split(":\\s*")[1].trim().split("\\s+");
					StringBuilder fullName = new StringBuilder();
					for (String part : parts) {
						if (part.matches("[a-zA-Z]+")) {
							fullName.append(part).append(" ");
						}
					}
					result.setName(fullName.toString().trim());
				} else {
					String[] parts = firstLine.split("\\s+");
					StringBuilder fullName = new StringBuilder();
					for (String part : parts) {
						if (part.matches("[a-zA-Z]+")) {
							fullName.append(part).append(" ");
						}
					}
					result.setName(fullName.toString().trim());
				}
			}
		}
	}
}
