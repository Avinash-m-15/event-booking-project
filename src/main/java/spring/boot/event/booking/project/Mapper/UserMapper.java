package spring.boot.event.booking.project.Mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import spring.boot.event.booking.project.DTO.UserRequest;
import spring.boot.event.booking.project.DTO.UserResponse;
import spring.boot.event.booking.project.Entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(UserRequest request);

    @Mapping(target = "id", source = "userId")
    UserResponse toResponse(User user);
}
