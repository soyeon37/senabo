package com.senabo.domain.emergency.service;

import com.senabo.common.message.ParsingMessageService;
import com.senabo.config.firebase.FCMService;
import com.senabo.domain.emergency.dto.response.EmergencyResponse;
import com.senabo.domain.emergency.entity.Emergency;
import com.senabo.domain.emergency.entity.EmergencyType;
import com.senabo.domain.emergency.repository.EmergencyRepository;
import com.senabo.domain.expense.entity.Expense;
import com.senabo.domain.expense.service.ExpenseService;
import com.senabo.domain.member.entity.Member;
import com.senabo.domain.stress.entity.StressType;
import com.senabo.domain.stress.service.StressService;
import com.senabo.domain.walk.entity.Walk;
import com.senabo.domain.walk.service.WalkService;
import com.senabo.exception.message.ExceptionMessage;
import com.senabo.exception.model.DataException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EmergencyService {
    private final FCMService fcmService;
    private final EmergencyRepository emergencyRepository;
    private final ParsingMessageService parsingMessageService;
    private final StressService stressService;
    private final WalkService walkService;
    private final ExpenseService expenseService;


    public void ramdomDayEmergency(Member member) {
        if (!ramdomSend()) return;
        EmergencyType type;
        EmergencyType[] types = {EmergencyType.POOP, EmergencyType.STOMACHACHE, EmergencyType.ANXIETY, EmergencyType.DEPRESSION};
        List<Emergency> list = getEmergencyLastWeek(member);
        int[] cnt = new int[4];
        for (Emergency emergency : list) {
            EmergencyType getType = emergency.getType();
            if (getType == EmergencyType.POOP) cnt[0]++;
            else if (getType == EmergencyType.STOMACHACHE) cnt[1]++;
            else if (getType == EmergencyType.ANXIETY) cnt[2]++;
            else if (getType == EmergencyType.DEPRESSION) cnt[3]++;
        }
        List<EmergencyType> typeList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (cnt[i] != 3) {
                typeList.add(types[i]);
            }
        }
        if (typeList.isEmpty()) return;

        type = comb(typeList);
        String body = "";
        String dogName = parsingMessageService.parseLastCharacter(member.getDogName());
        switch (type) {
            case POOP -> {
                body = "집에서 냄새가 나요";
                stressService.saveStress(member, StressType.POOP, 10);
            }
            case STOMACHACHE -> {
                body = dogName + "가 아픈 것 같아요";
                stressService.saveStress(member, StressType.STOMACHACHE, 10);
            }
            case ANXIETY -> {
                body = "외부 소음으로 인해 불안함을 느낍니다";
                stressService.saveStress(member, StressType.ANXIETY, 10);
            }
            case DEPRESSION -> {
                body = dogName + "가 무기력함을 느낍니다";
                stressService.saveStress(member, StressType.DEPRESSION, 10);
            }
        }
        fcmService.sendNotificationByToken("세상에 나쁜 보호자는 있다", body, member.getDeviceToken());
        saveEmergency(member, type);
    }

    public void ramdomEveningEmergency(Member member) {
        if (!ramdomSend()) return;
        EmergencyType type;
        EmergencyType[] types = {EmergencyType.CRUSH, EmergencyType.BITE};
        List<Emergency> list = getEmergencyLastWeek(member);
        int[] cnt = new int[2];
        for (Emergency emergency : list) {
            EmergencyType getType = emergency.getType();
            if (getType == EmergencyType.CRUSH) cnt[0]++;
            else if (getType == EmergencyType.BITE) cnt[1]++;
        }
        List<EmergencyType> typeList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            if (cnt[i] != 3) {
                typeList.add(types[i]);
            }
        }
        if (typeList.isEmpty()) return;

        type = comb(typeList);
        String body = "";
        String dogName = parsingMessageService.parseLastCharacter(member.getDogName());
        switch (type) {
            case CRUSH -> body = dogName + "가 잠을 자지 않아요";
            case BITE -> body = "물림 사고가 발생했어요!";
        }
        fcmService.sendNotificationByToken("세상에 나쁜 보호자는 있다", body, member.getDeviceToken());
        saveEmergency(member, type);
    }


    public boolean ramdomSend() {
        Random random = new Random();
        double randomValue = random.nextDouble();
        // 70%의 확률로 false, 30%의 확률로 true
        return randomValue < 0.3;
    }

    public EmergencyType comb(List<EmergencyType> types) {
        ;
        int randomIndex = new Random().nextInt(types.size());
        return types.get(randomIndex);
    }

    @Transactional
    public void saveEmergency(Member member, EmergencyType type) {
        emergencyRepository.save(new Emergency(member, type, false));
        try {
            emergencyRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DataException(ExceptionMessage.FAIL_SAVE_DATA);
        }
    }

    @Transactional
    public EmergencyResponse solvedEmergency(Member member, Long id) {
        Optional<Emergency> emergencyOptional = emergencyRepository.findByIdAndMemberId(id, member);
        if (emergencyOptional.isEmpty()) throw new DataException(ExceptionMessage.DATA_NOT_FOUND);
        Emergency emergency = emergencyOptional.get();
        emergency.update(true);
        return EmergencyResponse.from(emergency);
    }

    // 7일간 발생한 돌방상황 조회
    public List<Emergency> getEmergencyLastWeek(Member member) {
        return emergencyRepository.findLastWeekEmergency(member);
    }


    public void ramdomWalkWeekendEmergency(Member member) {
        List<Emergency> emergencyList = emergencyRepository.findByTypeToday(member, EmergencyType.WALK);
        // 보낸 적이 있으면 산책 나갔는지 검사
        if (!emergencyList.isEmpty()) {
            Optional<Walk> walkOptional = walkService.findLatestData(member);
            if(walkOptional.isEmpty()) stressService.saveStress(member, StressType.WALK, 30);
        } 
        // 보낸 적이 없으면 ramdom, 무조건 1번 알림
        else {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
            LocalDateTime eightAm = now.withHour(8).withMinute(0).withSecond(0).withNano(0);
            if ( ramdomSend() || now.isEqual(eightAm) || now.isAfter(eightAm)) {
                String dogName = parsingMessageService.parseLastCharacter(member.getDogName());
                String body = dogName + "가 산책을 가고 싶어해요";
                fcmService.sendNotificationByToken("세상에 나쁜 보호자는 있다", body, member.getDeviceToken());
            }
        }
    }

    public void ramdomBarkWeekendEmergency(Member member) {
        List<Emergency> emergencyList = emergencyRepository.findByTypeToday(member, EmergencyType.BARKING);
        if(!emergencyList.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime elevenPm = now.withHour(23).withMinute(0).withSecond(0).withNano(0);
        if(ramdomSend() || now.isEqual(elevenPm) || now.isAfter(elevenPm)){
            stressService.saveStress(member, StressType.BARKING, 20);
            String dogName = parsingMessageService.parseLastCharacter(member.getDogName());
            String body = dogName + "가 짖어서 민원이 들어올 수 있습니다";
            fcmService.sendNotificationByToken("세상에 나쁜 보호자는 있다", body, member.getDeviceToken());
        }
    }

    public void ramdomVomitingWeekendEmergency(Member member) {
        List<Emergency> emergencyList = emergencyRepository.findByTypeToday(member, EmergencyType.VOMITING);
        // 보낸 적이 있으면 병원에 갔는지 검사
        if (!emergencyList.isEmpty()) {
            List<Expense> expenseList = expenseService.findTodayExpense(member);
            if(expenseList.isEmpty()) stressService.saveStress(member, StressType.VOMITING, 30);
        }
        // 보낸 적이 없으면 ramdom
        else {
            if (ramdomSend()) {
                String dogName = parsingMessageService.parseLastCharacter(member.getDogName());
                String  body = dogName + "의 상태가 좋지 않습니다";
                fcmService.sendNotificationByToken("세상에 나쁜 보호자는 있다", body, member.getDeviceToken());
                stressService.saveStress(member, StressType.VOMITING, 10);
            }
        }
    }
}
