package com.example.roombooking.model;

public enum BookingStatus {
    BOOKED,      // 已預約，等待報到
    CHECKED_IN,  // 已報到
    RELEASED     // 逾時未報到，已自動釋放
}
