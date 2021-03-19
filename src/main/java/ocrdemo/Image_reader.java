package ocrdemo;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;

//
//public class Image_reader {
//    private String URL;
//    private String url_number;
//    public Image_reader(String URL,String number) {
//        this.URL = URL;
//        url_number = number;
//    }
//
//    public void setURL(String URL) {
//        this.URL = URL;
//    }
//
//    public String OCR(){
//        ITesseract img = new Tesseract();
////        BufferedReader input_file;  //text of URLs
//
//        try {
////            input_file = new BufferedReader(new FileReader(URL));
////            String line_url = input_file.readLine();
//            String line_url = URL;
//            String picture_not_numbered = "a";
////            int counter = 1;
////            int shitty_counter = 1;
//
//            if (line_url != null) {
//                String name_of_file = line_url.substring(line_url.lastIndexOf('/') + 1);
//
//                File tmp = new File(name_of_file);
//
//                try {
//                    FileUtils.copyURLToFile(new URL(line_url), tmp);    //download file from line_url to tmp
//                    System.out.println(line_url + "\n=========================");
//
//                    try {
//                        String msg = img.doOCR(tmp);
//                        System.out.println(msg);
////                        System.out.println(Integer.toString(counter) +"works");
////                        counter++;
//                        return msg;
//                    } catch (TesseractException e) {
//                        e.printStackTrace();
//                    }
//                    // read next line
//
//                } catch (IOException e) {
////                    System.out.println("\n error accrue " + e + "\n file failed num:" + shitty_counter);
////                    shitty_counter++;
//
//                }
//
//
////                line_url = input_file.readLine();
//            }
//
//
//
//
////            input_file.close();
//
//
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return "failed";
//    }
//}
