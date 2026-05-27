package spring.boot.event.booking.project.exception;

public class ObjectOptimisticLockingFailureException extends RuntimeException {
    public ObjectOptimisticLockingFailureException(String message) {
        super(message);
    }
}
