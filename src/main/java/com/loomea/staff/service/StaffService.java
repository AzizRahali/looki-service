package com.loomea.staff.service;

import com.loomea.booking.dto.BookingDto;
import com.loomea.booking.entity.Appointment;
import com.loomea.booking.repository.AppointmentRepository;
import com.loomea.auth.entity.User;
import com.loomea.common.exception.ResourceNotFoundException;
import com.loomea.owner.entity.StaffMember;
import com.loomea.owner.repository.StaffMemberRepository;
import com.loomea.staff.dto.StaffProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffMemberRepository staffMemberRepository;
    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public StaffProfileDto getProfile(UUID userId) {
        StaffMember member = findByUserId(userId);
        return toProfileDto(member);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getUpcomingBookings(UUID userId) {
        StaffMember member = findByUserId(userId);
        return appointmentRepository.findUpcomingBySalon(member.getSalon().getId(), LocalDate.now())
                .stream()
                .filter(a -> a.getStaffMember() != null && a.getStaffMember().getId().equals(member.getId()))
                .map(this::toBookingDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getPastBookings(UUID userId) {
        StaffMember member = findByUserId(userId);
        return appointmentRepository.findPastBySalon(member.getSalon().getId(), LocalDate.now())
                .stream()
                .filter(a -> a.getStaffMember() != null && a.getStaffMember().getId().equals(member.getId()))
                .map(this::toBookingDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getTodayBookings(UUID userId) {
        StaffMember member = findByUserId(userId);
        return appointmentRepository.findTodayBySalon(member.getSalon().getId(), LocalDate.now())
                .stream()
                .filter(a -> a.getStaffMember() != null && a.getStaffMember().getId().equals(member.getId()))
                .map(this::toBookingDto)
                .toList();
    }

    private StaffMember findByUserId(UUID userId) {
        return staffMemberRepository.findByLinkedUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffMember", "linkedUserId", userId));
    }

    private StaffProfileDto toProfileDto(StaffMember m) {
        return StaffProfileDto.builder()
                .staffId(m.getId().toString())
                .name(m.getName())
                .role(m.getRole())
                .avatarUrl(m.getAvatarData() != null && m.getAvatarData().length > 0
                        ? "/api/owner/staff/" + m.getId() + "/avatar"
                        : null)
                .salonId(m.getSalon().getId().toString())
                .salonName(m.getSalon().getName())
                .salonAddress(m.getSalon().getAddress())
                .build();
    }

    private BookingDto toBookingDto(Appointment a) {
        User client = a.getUser();
        return BookingDto.builder()
                .id(a.getId().toString())
                .salonId(a.getSalon().getId().toString())
                .salonName(a.getSalon().getName())
                .salonAddress(a.getSalon().getAddress())
                .date(a.getDate())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus().name())
                .paymentMethod(a.getPaymentMethod().name())
                .notes(a.getNotes())
                .totalPrice(a.getTotalPrice())
                .clientId(client != null ? client.getId().toString() : "")
                .clientName(client != null ? client.getFullName() : "")
                .services(a.getServices().stream()
                        .map(s -> BookingDto.BookingServiceDto.builder()
                                .serviceName(s.getServiceName())
                                .price(s.getPrice())
                                .durationMinutes(s.getDurationMinutes())
                                .build())
                        .toList())
                .build();
    }
}
