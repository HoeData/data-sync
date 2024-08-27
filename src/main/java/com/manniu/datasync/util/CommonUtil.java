package com.manniu.datasync.util;


import com.manniu.datasync.doMain.FileDo;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommonUtil {
    private static long splitSize = 1024*1024;
    //切割文件
    public static List<FileDo> splitFile(File file) {
        List<FileDo>  res = new ArrayList<>();
        //获取当前文件的 父级目录
        File parentFile = file.getParentFile();
        //先创建分割文件
        File splitFile = new File(parentFile,"split");
        if(!splitFile.exists()){
            splitFile.mkdirs();
        }
        try {
            //根据长度进行读取文件
            RandomAccessFile randomAccessFile = new RandomAccessFile(file,"r");
            //计算要分割多少次
            long splitTime = file.length()/splitSize;
            if(file.length()%splitSize!=0){
                splitTime++;
            }
            for (int i = 0; i < splitTime; i++) {
                long start = i*splitSize;
                long end = Math.min(start+splitSize,file.length());
                byte[] buff = new byte[(int)(end-start)];
                //设置读取开始位置
                randomAccessFile.seek(start);
                //设置读取大小
                randomAccessFile.read(buff);
                String path = splitFile+File.separator+i+".part";
                try(FileOutputStream out = new FileOutputStream(path)){
                    out.write(buff);
                }
                res.add(new FileDo(i+".part",path, Instant.now().toEpochMilli()));
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException("文件不存在");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return res;
    }

    //传输的参数一定是文件夹
    public static void mergeFile(File dir,File tagFile) throws IOException {
        //分段的集合
        File[] files = dir.listFiles((file, name) -> name.endsWith(".part"));
        //先进行排序
        Arrays.sort(files,(f1,f2)->{
            Integer a1 = Integer.parseInt(f1.getName().split("\\.")[0]);
            Integer a2 = Integer.parseInt(f2.getName().split("\\.")[0]);
            return a1.compareTo(a2);
        });
        //开始合并
        try(FileOutputStream out = new FileOutputStream(tagFile)){
            for (File file : files) {
                FileInputStream is = new FileInputStream(file);
                byte[] buff = new byte[1024];
                int length = 0;
                while((length = is.read(buff))!=-1){
                    out.write(buff,0,length);
                }
                is.close();
            }
        }
    }
}
