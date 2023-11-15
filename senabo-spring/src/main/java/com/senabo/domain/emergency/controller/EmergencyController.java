package com.senabo.domain.emergency.controller;

import com.senabo.common.api.ApiResponse;
import com.senabo.domain.emergency.dto.response.EmergencyResponse;
import com.senabo.domain.emergency.entity.Emergency;
import com.senabo.domain.emergency.service.EmergencyService;
import com.senabo.domain.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/emergency")
@Tag(name = "Emergency", description = "Emergency API Document")
public class EmergencyController {

    private final EmergencyService emergencyService;
    private final MemberService memberService;

    @PutMapping("/solve")
    @Operation(summary = "돌발상황 해결 업데이트", description = "발생한 돌발 상황을 해결한다")
    public ApiResponse<EmergencyResponse> solvedProblem(@AuthenticationPrincipal UserDetails principal, @RequestParam Long id) {
        EmergencyResponse response = emergencyService.solvedEmergency(memberService.findByEmail(principal.getUsername()), id);
        return ApiResponse.success("돌발상황 해결 성공", response);
    }

//    @GetMapping("/get")
//    @Operation(summary = "최근 7일 내 돌발상황 조회", description = "최근 7일 내 발생한 돌발상황을 조회한다. 7일이 지나면 돌발상황이 무효된 것으로 간주한다.")
//    public ApiResponse<List<EmergencyResponse>> getEmergencyLastWeek(@AuthenticationPrincipal UserDetails principal) {
//        List<Emergency> emergency = emergencyService.getEmergencyLastWeek(memberService.findByEmail(principal.getUsername()));
//        if (emergency.isEmpty()) return ApiResponse.fail("일주일 간 발생한 돌발상황 조회 실패", null);
//        List<EmergencyResponse> response = emergency.stream()
//                .map(EmergencyResponse::from)
//                .collect(Collectors.toList());
//        return ApiResponse.success("일주일 간 발생한 돌발상황 조회 성공", response);
//    }

    @GetMapping("/get")
    @Operation(summary = "해결 못한 가장 최신 돌발상황 조회", description = "해결 못한 가장 최신 돌발상황 조회한다. 7일이 지나면 돌발상황이 무효된 것으로 간주한다.")
    public ApiResponse<Map<String, EmergencyResponse>> getEmergencyLastWeekUnSolved(@AuthenticationPrincipal UserDetails principal) {
        Map<String, EmergencyResponse> emergency = emergencyService.getEmergencyLastWeekUnSolved(memberService.findByEmail(principal.getUsername()));
        return ApiResponse.success("해결 못한 가장 최신 돌발상황 조회 성공", emergency);
    }
}
