package org.javerland.homecenter.api;

import org.javerland.homecenter.auth.InvalidLoginException;
import org.javerland.homecenter.media.MediaNotFoundException;
import org.javerland.homecenter.scan.ScanAlreadyRunningException;
import org.javerland.homecenter.source.DuplicateSourceNameException;
import org.javerland.homecenter.source.NoActiveSourceException;
import org.javerland.homecenter.source.SmbAccessException;
import org.javerland.homecenter.stream.RangeNotSatisfiableException;
import org.javerland.homecenter.user.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** REST API errors in RFC 9457 format. The management UI has separate HTML handling. */
@RestControllerAdvice(basePackages = "org.javerland.homecenter.api")
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(MediaNotFoundException.class)
    ProblemDetail handleMediaNotFound(MediaNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Médium nenájdené", ex.getMessage());
    }

    @ExceptionHandler(InvalidLoginException.class)
    ProblemDetail handleInvalidLogin(InvalidLoginException ex) {
        // Omit the name from the log; a failed attempt is often followed by the correct one.
        log.info("Neúspešné prihlásenie cez API");
        return problem(HttpStatus.UNAUTHORIZED, "Prihlásenie zlyhalo", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Používateľ nenájdený", ex.getMessage());
    }

    @ExceptionHandler(NoActiveSourceException.class)
    ProblemDetail handleNoSource(NoActiveSourceException ex) {
        return problem(HttpStatus.CONFLICT, "Chýba Samba zdroj", ex.getMessage());
    }

    @ExceptionHandler(DuplicateSourceNameException.class)
    ProblemDetail handleDuplicateSource(DuplicateSourceNameException ex) {
        return problem(HttpStatus.CONFLICT, "Názov zdroja je obsadený", ex.getMessage());
    }

    @ExceptionHandler(ScanAlreadyRunningException.class)
    ProblemDetail handleScanRunning(ScanAlreadyRunningException ex) {
        return problem(HttpStatus.CONFLICT, "Sken už beží", ex.getMessage());
    }

    @ExceptionHandler(SmbAccessException.class)
    ProblemDetail handleSmbAccess(SmbAccessException ex) {
        log.warn("Prístup na Sambu zlyhal: {}", ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Úložisko je nedostupné", ex.getMessage());
    }

    @ExceptionHandler(RangeNotSatisfiableException.class)
    ResponseEntity<ProblemDetail> handleRange(RangeNotSatisfiableException ex) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                // RFC 9110 requires a 416 response to report the file's actual length.
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + ex.totalLength())
                .body(problem(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Neplatný rozsah", ex.getMessage()));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
