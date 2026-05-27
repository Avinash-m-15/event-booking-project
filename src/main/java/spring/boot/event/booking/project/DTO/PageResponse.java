package spring.boot.event.booking.project.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor // <-- This is exactly what Jackson needs to read from Redis!
public class PageResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    // A helpful constructor to instantly convert Spring's Page into our custom DTO
    public PageResponse(Page<T> page) {
        this.content = page.getContent();
        this.number = page.getNumber();
        this.size = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.last = page.isLast();
    }
}