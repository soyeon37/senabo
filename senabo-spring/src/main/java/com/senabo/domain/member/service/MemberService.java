package com.senabo.domain.member.service;


import com.senabo.config.firebase.FCMService;
import com.senabo.config.security.jwt.TokenInfo;
import com.senabo.config.security.jwt.TokenProvider;
import com.senabo.domain.member.dto.request.SignInRequest;
import com.senabo.domain.member.dto.request.SignOutRequest;
import com.senabo.domain.member.dto.request.SignUpRequest;
import com.senabo.domain.member.dto.request.UpdateInfoRequest;
import com.senabo.domain.member.dto.response.CheckEmailResponse;
import com.senabo.domain.member.dto.response.MemberResponse;
import com.senabo.domain.member.dto.response.ReIssueResponse;
import com.senabo.domain.member.dto.response.SignInResponse;
import com.senabo.domain.member.entity.Member;
import com.senabo.domain.member.entity.Role;
import com.senabo.domain.member.repository.MemberRepository;
import com.senabo.domain.report.entity.Report;
import com.senabo.domain.report.repository.ReportRepository;
import com.senabo.exception.message.ExceptionMessage;
import com.senabo.exception.model.TokenCheckFailException;
import com.senabo.exception.model.TokenNotFoundException;
import com.senabo.exception.model.UserAuthException;
import com.senabo.exception.model.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final ReportRepository reportRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenProvider tokenProvider;
    private final FCMService fcmService;

    public MemberResponse signUp(SignUpRequest request) {
        Member member = memberRepository.save(
                new Member(request.dogName(), request.email(), request.species(), request.sex(), request.houseLatitude(), request.houseLongitude(), request.deviceToken()));

        reportRepository.save(
                new Report(member, 1, 0, 50)
        );
        try {
            memberRepository.flush();
            reportRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new UserAuthException(String.valueOf(ExceptionMessage.FAIL_SAVE_DATA));
        }

        List<GrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority(Role.ROLE_USER.toString()));

        Authentication authentication = new UsernamePasswordAuthenticationToken(member.getEmail(), null, roles);

        TokenInfo tokenInfo = tokenProvider.generateToken(authentication);

        refreshTokenService.setValues(tokenInfo.getRefreshToken(), member.getEmail());

        return MemberResponse.from(member);
    }

    @Transactional
    public void removeMember(String email, SignOutRequest request) {
        try {
            refreshTokenService.delValues(request.refreshToken());
            memberRepository.deleteByEmail(email);
        } catch (DataIntegrityViolationException e) {
            throw new UserAuthException(ExceptionMessage.FAIL_DELETE_DATA);
        }
    }

    @Transactional
    public MemberResponse getInfo(String email) {
        Member member = findByEmail(email);
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateInfo(String email, UpdateInfoRequest request) {
        Member member = findByEmail(email);
        member.update(request);
        return MemberResponse.from(member);
    }


    @Transactional
    public SignInResponse signIn(SignInRequest request) {
        Optional<Member> memberOptional = memberRepository.findByEmail(request.email());
        if (memberOptional.isEmpty()) {
            // 미 가입자
            return SignInResponse.emptyMember(false);
        }

        Member member = memberOptional.get();
        // 유효한 가입자 -> jwt 발급 및 로그인 진행
        List<GrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority(Role.ROLE_USER.toString()));

        // jwt 발급
        Authentication authentication = new UsernamePasswordAuthenticationToken(request.email(), null, roles);
        TokenInfo tokenInfo = tokenProvider.generateToken(authentication);

        refreshTokenService.setValues(tokenInfo.getRefreshToken(), request.email());

        return SignInResponse.from(member, tokenInfo, true);
    }

    @Transactional
    public void signOut(SignOutRequest request, String email) {
        Member member = findByEmail(email);
        member.setDeviceToken(null);
        refreshTokenService.delValues(request.refreshToken());
    }

    @Transactional
    public ReIssueResponse reissue(String refreshToken, Authentication authentication) {
        if (authentication.getName() == null) {
            throw new UserAuthException(ExceptionMessage.NOT_AUTHORIZED_ACCESS);
        }

        if (!tokenProvider.validateToken(refreshToken)) {
            Member member = findByEmail(authentication.getName());
            member.setDeviceToken(null);
            refreshTokenService.delValues(refreshToken);
            throw new TokenNotFoundException(ExceptionMessage.TOKEN_VALID_TIME_EXPIRED);
        }

        String email = refreshTokenService.getValues(refreshToken);
        if (email == null || !email.equals(authentication.getName())) {
            throw new TokenCheckFailException(ExceptionMessage.MISMATCH_TOKEN);
        }

        return createAccessToken(refreshToken, authentication);
    }

    private ReIssueResponse createAccessToken(String refreshToken, Authentication authentication) {
        if (tokenProvider.checkExpiredToken(refreshToken)) {
            TokenInfo tokenInfo = tokenProvider.generateAccessToken(authentication);
            return ReIssueResponse.from(tokenInfo.getAccessToken(), "SUCCESS");
        }

        return ReIssueResponse.from(tokenProvider.generateAccessToken(authentication).getAccessToken(), "GENERAL_FAILURE");
    }

    @Transactional
    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email).orElseThrow(() -> new UserException(ExceptionMessage.USER_NOT_FOUND));
    }

    @Transactional
    public List<Member> findAllMember(){
        return memberRepository.findAll();
    }

    public void fcmTest(String deviceToken) {
        log.info("FCM 테스트 시작");
        fcmService.sendNotificationByToken("세상에 나쁜 보호자는 있다", LocalDateTime.now() + ": FCM 테스트", deviceToken);
    }
}
