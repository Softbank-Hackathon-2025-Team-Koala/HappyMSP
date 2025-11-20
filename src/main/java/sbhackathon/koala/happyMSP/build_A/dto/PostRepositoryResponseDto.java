package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostRepositoryResponseDto {
    private final PostRepositoryState state;
    private final RepositoryDto repository;

    public static PostRepositoryResponseDto success(RepositoryDto repository) {
        return PostRepositoryResponseDto.builder()
                .state(PostRepositoryState.SUCCESS)
                .repository(repository)
                .build();
    }

    public static PostRepositoryResponseDto alreadyDeployed(RepositoryDto repository) {
        return PostRepositoryResponseDto.builder()
                .state(PostRepositoryState.ALREADY_DEPLOYED)
                .repository(repository)
                .build();
    }
}