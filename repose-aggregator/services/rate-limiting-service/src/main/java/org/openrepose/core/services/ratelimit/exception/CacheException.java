package org.openrepose.core.services.ratelimit.exception;

public class CacheException extends RuntimeException {

   public CacheException(String message, Throwable t) {
      super(message, t);
   }
}
