package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sbhackathon.koala.happyMSP.entity.ServiceStatus;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateServiceStatusRequestDto {
    private ServiceStatus status;
}