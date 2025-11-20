package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;
import sbhackathon.koala.happyMSP.entity.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class RepositoryDto {
    private final int repoId;
    private final String repoUrl;
    private final String latestCommit;
    private final List<ServiceDto> services;

    public static RepositoryDto from(Repository repository) {
        List<ServiceDto> serviceDtos = repository.getServices().stream()
                .map(ServiceDto::from)
                .collect(Collectors.toList());

        return RepositoryDto.builder()
                .repoId(repository.getId())
                .repoUrl(repository.getUri())
                .latestCommit(repository.getLatestCommit())
                .services(serviceDtos)
                .build();
    }
}