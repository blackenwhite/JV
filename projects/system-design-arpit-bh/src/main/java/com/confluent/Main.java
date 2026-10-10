package com.confluent;

import java.io.RandomAccessFile;
import java.util.Arrays;

public class Main {
//    public static void main(String[] args) throws Exception {
//        String content = Files.readString(Path.of("sample.txt"));
//        System.out.println(content);
//    }

    /*public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader("sample.txt"));
        String line;
        while((line= reader.readLine())!=null) {
            System.out.println(line);
        }
        reader.close();
    }*/

    public static void main(String[] args) throws Exception{
//        RandomAccessFile file = new RandomAccessFile("sample.txt", "r");
//        long size = file.length();
//        System.out.println(size);
//
//        file.seek(size);
//
//        System.out.println("current position: " + file.getFilePointer());
//
//        file.close();
        doExercise();

    }

    public static void doExercise() throws Exception {
        // print the file size
        RandomAccessFile file = new RandomAccessFile("sample.txt", "r");
        System.out.println(file.length());

        // move to position 0 and read 1 byte
        file.seek(0);
        System.out.println(file.read());

        // move to position 6 and read one byte

        file.seek(6);
        System.out.println(file.read());

        //
        byte[] buffer = new byte[5];
        file.read(buffer);
        System.out.println(Arrays.toString(buffer));

        file.seek(file.length());
        System.out.println(file.getFilePointer());
    }
}
