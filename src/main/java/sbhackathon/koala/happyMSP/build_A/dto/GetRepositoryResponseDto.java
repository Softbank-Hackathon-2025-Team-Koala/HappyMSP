package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GetRepositoryResponseDto {
    private final RepositoryState state;
    private final RepositoryDto repository;

    public static GetRepositoryResponseDto of(RepositoryState state, RepositoryDto repository) {
        return GetRepositoryResponseDto.builder()
                .state(state)
                .repository(repository)
                .build();
    }

    public static GetRepositoryResponseDto notExist() {
        return GetRepositoryResponseDto.builder()
                .state(RepositoryState.NO_EXIST)
                .repository(null)
                .build();
    }
}