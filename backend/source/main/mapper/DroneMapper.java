package mapper;

import dto.response.DroneResponse;
import entity.Drone;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DroneMapper {

    DroneResponse toDroneResponse(Drone drone);
}





