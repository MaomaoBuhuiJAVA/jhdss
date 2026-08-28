package com.jhds.controller.dto;

import lombok.Data;

@Data
public class AlarmUpdateRequest {

    /** pending, processing, or resolved. Omit to change only the memo. */
    private String status;

    /** Empty text clears a previously saved memo. */
    private String handlingMemo;
}
