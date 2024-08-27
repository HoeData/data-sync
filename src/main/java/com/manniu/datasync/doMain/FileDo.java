package com.manniu.datasync.doMain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileDo {
    private String fileName;
    private String filePath;
    private Long createTime;
}
