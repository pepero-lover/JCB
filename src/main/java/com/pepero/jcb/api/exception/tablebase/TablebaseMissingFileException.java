package com.pepero.jcb.api.exception.tablebase;

public class TablebaseMissingFileException extends RuntimeException {
     public TablebaseMissingFileException(String message) {
         super(message);
     }

     public TablebaseMissingFileException(String message, Throwable cause) {
         super(message, cause);
     }
 }