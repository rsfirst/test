package com.fis.fb8.service.impl;

import com.fis.fb8.constant.DateTimeConstants;
import com.fis.fb8.constant.ErrorCode;
import com.fis.fb8.dto.*;
import com.fis.fb8.exception.BusinessException;
import com.fis.fb8.repository.FileRepository;
import com.fis.fb8.service.FileService;
import com.fis.fb8.util.DateUtil;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Security;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.fis.fb8.util.FileUtil.checkExtensionFile;
import static com.fis.fb8.util.FileUtil.getMD5ChecksumByImgPcr231;

/**
 * @author TNV6996
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Value("${url.path}")
    private String URL_PATH = "";

    @Value("${extension.file}")
    private String EXTENDSION_FILE = "";
    @Value("${mine.type}")
    private String MINE_TYPE = "";
    @Value("${default.img.convert.after.add.date}")
    private String DEFAULT_CONVERT_FILE = "";

    @Autowired
    FileRepository fileRepository;
    @Value("${sftp.host}")
    private String SFTPHOST = "";
    @Value("${sftp.port}")
    private int SFTPPORT;
    @Value("${sftp.user.name}")
    private String SFTPUSER = "";
    @Value("${sftp.password}")
    private String SFTPPASS = "";
    @Value("${sftp.timeout}")
    private int timeout;


    @Override
    public UploadFileDTO uploadFile(UploadFileReq uploadFileReq, MultipartFile multipartFile) throws Exception{
        long startTime = System.currentTimeMillis();
        log.info("startTime Upload file -> " + uploadFileReq.getFileName() + " - " +multipartFile.getOriginalFilename() +" -> " + uploadFileReq.getUserName()
                +" -> " + uploadFileReq.getIsInsertDate() + " -> startTime: " + startTime);
        if (!StringUtils.hasLength(uploadFileReq.getFileName())) {
            log.info("EndTime Upload file -> " + uploadFileReq.getFileName()  + " - " +multipartFile.getOriginalFilename() +" -> " + uploadFileReq.getUserName()
                    +" -> " + uploadFileReq.getIsInsertDate() + " -> EndTime: " + (System.currentTimeMillis() - startTime));
             throw new BusinessException(ErrorCode.E0501002);
        }
        String partFileExtension = FilenameUtils.getExtension(multipartFile.getOriginalFilename());
        String partFileName = FilenameUtils.getName(multipartFile.getOriginalFilename());
        /*      if (!uploadFileReq.getFileName().equals(partFileName)) {
             throw new BusinessException(ErrorCode.E0501003);
        }*/
        DocumentTypeDTO fiFmsApplication = fileRepository.getDocumentType(uploadFileReq.getDocumentTypeId());
        if (!StringUtils.hasLength(uploadFileReq.getDocumentTypeId()) || Objects.isNull(fiFmsApplication) ) {
            log.info("EndTime Upload file -> " + uploadFileReq.getFileName()  + " - " +multipartFile.getOriginalFilename() +" -> " + uploadFileReq.getUserName()
                    +" -> " + uploadFileReq.getIsInsertDate() + " -> EndTime: " + (System.currentTimeMillis() - startTime));
             throw new BusinessException(ErrorCode.E0501004);
        }
        if (!checkExtensionFile(multipartFile,MINE_TYPE,EXTENDSION_FILE)) {
            log.info("EndTime Upload file -> " + uploadFileReq.getFileName()  + " - " +multipartFile.getOriginalFilename() +" -> " + uploadFileReq.getUserName()
                    +" -> " + uploadFileReq.getIsInsertDate() + " -> EndTime: " + (System.currentTimeMillis() - startTime));
            throw new BusinessException(ErrorCode.E0501005);
        }
        String hashFileName = com.fis.fb8.util.StringUtils.md5Digest(multipartFile.getOriginalFilename().replace(".".concat(
                com.fis.fb8.util.StringUtils.nvl(partFileExtension,"")), ""));
        String formatFileName =
                String.format("%s_%s%s", hashFileName, System.currentTimeMillis(), ".".concat(com.fis.fb8.util.StringUtils.nvl(partFileExtension,"")));
        StringBuilder filePath;
        Date currentDate = new Date();
        String dateString = DateUtil.toStringFromDate(currentDate, DateTimeConstants.YYYY_MM_DD_SLASH);
        String hoursString = DateUtil.toStringFromDate(currentDate, DateTimeConstants.HH_MM_SLASH);
        if (!"".equals(com.fis.fb8.util.StringUtils.nvl(uploadFileReq.getFilePath(), ""))) {
            filePath = new StringBuilder();
            filePath.append(fiFmsApplication.getRootFolderPath()).append(uploadFileReq.getFilePath()).append(uploadFileReq.getUserName());
        }else{
            filePath = new StringBuilder();
            filePath.append(fiFmsApplication.getRootFolderPath())
                    .append("/")
                    .append(dateString)
                    .append("/")
                    .append(uploadFileReq.getUserName())
                    .append("/")
                    .append(hoursString);
        }
        FileOutput fileOutput = saveFile(filePath.toString(), formatFileName, multipartFile, uploadFileReq.getIsInsertDate());
        if (!"".equals(com.fis.fb8.util.StringUtils.nvl(fileOutput.getFileId(), ""))
                && fileOutput.getFileLength() != null) {
            String fileId = fileOutput.getFileId();
            log.info("file Id create -> " + fileId);
            if(!"".equals(com.fis.fb8.util.StringUtils.nvl(uploadFileReq.getFileId(),""))){
                fileId = uploadFileReq.getFileId();
                log.info("file Id replace -> " + fileId);
            }
            AddDocumentFileDTO addFmsFile = AddDocumentFileDTO.builder()
                    .fileId(fileId)
                    .fileLength(fileOutput.getFileLength())
                    .fileName(multipartFile.getOriginalFilename())
                    .documentTypeId(uploadFileReq.getDocumentTypeId())
                    .url(URL_PATH)
                    .filePath(filePath.toString() + "/" + formatFileName)
                    .createUser(uploadFileReq.getUserName())
                    .build();
            fileRepository.addNewFile(addFmsFile);
            //bỏ sftp
            /*long lgStart = System.currentTimeMillis();
            log.info("ConnectSFTP start: " + lgStart);
            connectSFTP(addFmsFile.getFilePath(),filePath.toString(), uploadFileReq.getUserName());
            log.info("connectSFTP : End time : " + (new Date()).toString() + " Totaltime : " +
                    (System.currentTimeMillis() - lgStart));*/
            log.info("EndTime Upload file -> " + uploadFileReq.getFileName() + " - " +multipartFile.getOriginalFilename() +" -> " + uploadFileReq.getUserName()
                    +" -> " + uploadFileReq.getIsInsertDate() + " -> EndTime: " + (System.currentTimeMillis() - startTime));
            return UploadFileDTO.builder()
                    .fileId(fileId)
                    .documentTypeId(fiFmsApplication.getDocumentTypeId())
                    .fileName(multipartFile.getOriginalFilename())
                    .filePath(filePath.toString()+ "/" + formatFileName)
                    .createUser(uploadFileReq.getUserName())
                    .createDate(currentDate)
                    .fileSize(multipartFile.getSize())
                    .build();
        }
        log.info("EndTime Upload file -> " + uploadFileReq.getFileName() +" -> " + uploadFileReq.getUserName()
                +" -> " + uploadFileReq.getIsInsertDate() + " -> EndTime: " + (System.currentTimeMillis() - startTime));
        return null;
    }

    @Override
    public UploadFileRes uploadMultipleFile(List<UploadFileReq> uploadFileReq, MultipartFile[] multipartFile) throws Exception{
        UploadFileRes uploadFileRes = new UploadFileRes();
        List<UploadFileDTO> uploadFileDtoS = new ArrayList<>();
        List<FileErrorDTO> errorUploadFileNames = new ArrayList<>();
        for (int i = 0; i < multipartFile.length; ++i) {
            try {
                UploadFileDTO uploadFileDTO = uploadFile(uploadFileReq.get(i), multipartFile[i]);
                if (Objects.nonNull(uploadFileDTO)) {
                    uploadFileDtoS.add(uploadFileDTO);
                } else {
                    FileErrorDTO fileErrorDTO = new FileErrorDTO();
                    fileErrorDTO.setFileName(uploadFileReq.get(i).getFileName());
                    fileErrorDTO.setErrorCode(ErrorCode.SYSTEM_ERROR);
                    fileErrorDTO.setErrorCode("Upload loi!");
                    errorUploadFileNames.add(fileErrorDTO);
                }
            } catch (Exception ex) {
                FileErrorDTO fileErrorDTO = new FileErrorDTO();
                if(ex instanceof BusinessException) {
                    BusinessException businessException = (BusinessException) ex;
                    fileErrorDTO.setFileName(uploadFileReq.get(i).getFileName());
                    fileErrorDTO.setErrorCode(businessException.getErrorCode());
                    fileErrorDTO.setErrorContent(businessException.getContent());
                }else{
                    fileErrorDTO.setFileName(uploadFileReq.get(i).getFileName());
                    fileErrorDTO.setErrorCode(ErrorCode.SYSTEM_ERROR);
                    fileErrorDTO.setErrorContent(ex.getMessage());
                }
                errorUploadFileNames.add(fileErrorDTO);
            }
        }
        uploadFileRes.setUploadFiles(uploadFileDtoS);
        uploadFileRes.setErrorUploadFileNames(errorUploadFileNames);
        return uploadFileRes;
    }

    @Override
    public DownloadFileByteRes downloadFile(DownloadFileReq downloadFileReq) throws Exception {
        if (!StringUtils.hasLength(downloadFileReq.getFileId()) ||
                !StringUtils.hasLength(downloadFileReq.getDocumentTypeId())) {
            throw new BusinessException(ErrorCode.E0501002);
        }
        FmsFileDTO fmsFile = fileRepository.getFile(downloadFileReq.getFileId(),downloadFileReq.getDocumentTypeId());
        if (Objects.isNull(fmsFile) || "".equals(com.fis.fb8.util.StringUtils.nvl(fmsFile.getFilePath(),""))) {
            throw new BusinessException(ErrorCode.E0501002);
        }
        String filePaths = fmsFile.getFilePath();
        DownloadFileByteRes downloadFileByteRes = new DownloadFileByteRes();
        downloadFileByteRes.setFileName(fmsFile.getFileName());
        downloadFileByteRes.setFileBody(getFile(filePaths));
        return downloadFileByteRes;
    }

    @Override
    public DownloadFileRes downloadFileAsBase64(DownloadFileReq downloadFileReq) throws Exception {
        DownloadFileRes downloadFileRes = new DownloadFileRes();
        if (!StringUtils.hasLength(downloadFileReq.getFileId()) ||
                !StringUtils.hasLength(downloadFileReq.getDocumentTypeId())) {
            throw new BusinessException(ErrorCode.E0501002);
        }
        FmsFileDTO fmsFile = fileRepository.getFile(downloadFileReq.getFileId(),downloadFileReq.getDocumentTypeId());
        if (Objects.isNull(fmsFile) || "".equals(com.fis.fb8.util.StringUtils.nvl(fmsFile.getFilePath(),""))) {
            throw new BusinessException(ErrorCode.E0501002);
        }
        downloadFileRes.setFileName(fmsFile.getFileName());
        String filePaths = fmsFile.getFilePath();
        String hasRequest = new String(org.apache.commons.codec.binary.Base64.encodeBase64(getFile(filePaths)), StandardCharsets.UTF_8);
        downloadFileRes.setFileBody(hasRequest);
        return downloadFileRes;
    }

    private byte[] getFile(String filePath) {
        InputStream in;
        try {
            File file = new File(filePath);
            in = new FileInputStream(file);
            return IOUtils.toByteArray(in);
        } catch (Exception ex) {
            log.error(ex.getMessage(),ex);
            throw new BusinessException(ErrorCode.E0501009);
        }
    }

    private FileOutput saveFile(String filePath, String fileName, MultipartFile file, String isInsertDate) throws Exception{
        String fileId;
        long fileSize;
        FileOutput fileOutput = new FileOutput();
        InputStream inputStream = null;
        try {
            byte[] contents = file.getBytes();
            fileId = getMD5ChecksumByImgPcr231(contents);
            fileSize = file.getSize();
            fileOutput.setFileId(fileId);
            fileOutput.setFileLength(fileSize);
            Files.createDirectories(Paths.get(filePath));
            log.info("Path dir: {} ", filePath);
            Path path = Paths.get(filePath + File.separator + fileName);
            log.info("Path upload: {}", path);
            log.info("isInsertDate: {}", isInsertDate);
            String partFileExtension = FilenameUtils.getExtension(fileName);
            inputStream = file.getInputStream();
            try {
                if("1".equals(com.fis.fb8.util.StringUtils.nvl(isInsertDate,""))) {
                    if ("JPG".equals(com.fis.fb8.util.StringUtils.nvl(partFileExtension, "").toUpperCase())
                            || "JPEG".equals(com.fis.fb8.util.StringUtils.nvl(partFileExtension, "").toUpperCase())
                            || "PNG".equals(com.fis.fb8.util.StringUtils.nvl(partFileExtension, "").toUpperCase())) {
                        String value = com.fis.fb8.util.StringUtils.nvl(fileRepository.getValueApDomain(
                                "TIMESTAMP_ON_IMAGE", "1"), "0");
                        //RsFirst -> PCR231 insert ca gio
                        DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                        Date date = new Date();
                        date = DateUtil.addMinute(date, Integer.parseInt(value));
                        ImageIcon icon = new ImageIcon(contents);
                        // create BufferedImage object of same width and height as of original image
                        BufferedImage bufferedImage = new BufferedImage(icon.getIconWidth(),
                                icon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics g = bufferedImage.getGraphics();
                        int width = icon.getIconWidth();
                        int height = icon.getIconHeight();
                        Rectangle rectangle = new Rectangle(width, height);
                        FontMetrics metrics;
                        int fontSize = 98;
                        Graphics2D g2 = (Graphics2D) g;
                        do {
                            fontSize -= 2;
                            g2.setFont(g2.getFont().deriveFont((float) fontSize));
                            metrics = g2.getFontMetrics(g2.getFont());
                        } while (metrics.stringWidth(dateFormat.format(date)) > rectangle.width / 4);
                        int x = (int) Math.round((double) width / 100) + fontSize;
                        int y = (int) Math.round((double) height / 100) + fontSize;
                        g2.drawImage(icon.getImage(), 0, 0, null);
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setColor(Color.white);
                        g2.drawString(dateFormat.format(date), x - 1, y - 1);
                        g2.drawString(dateFormat.format(date), x + 1, y - 1);
                        g2.drawString(dateFormat.format(date), x - 1, y + 1);
                        g2.drawString(dateFormat.format(date), x + 1, y + 1);
                        g2.setColor(Color.black);
                        g2.drawString(dateFormat.format(date), x, y);
                        g2.dispose();
                        g.dispose();
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        ImageIO.write(bufferedImage, "".equals(com.fis.fb8.util.StringUtils.nvl(DEFAULT_CONVERT_FILE, ""))
                                ? partFileExtension : DEFAULT_CONVERT_FILE, out);
                        byte[] bytes = out.toByteArray();
                        inputStream = new ByteArrayInputStream(bytes);
                        fileOutput.setFileLength((long) bytes.length);
                        /*if(!"".equals(com.fis.fb8.util.StringUtils.nvl(DEFAULT_CONVERT_FILE, ""))
                                && !partFileExtension.toUpperCase().equals(DEFAULT_CONVERT_FILE.toUpperCase())){
                            String fileNameTemp = fileName.substring(0,fileName.lastIndexOf("."));
                            path = Paths.get(filePath + File.separator + fileNameTemp + "." + DEFAULT_CONVERT_FILE);
                            log.info("Path upload replace: {}", path);
                        }*/

                    }
                }
                Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }

            //return fileOutput;
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            throw ex;
        }finally {
            if(inputStream != null){
                inputStream.close();
            }
        }
        return fileOutput;
    }
    private void connectSFTP(String fileName,String filePath, String userName){
        String nomeMeToDo = ".connectSFTP() ";
        log.info(log.getName() + nomeMeToDo + " user: "
                + userName +" BEGIN -> " +  fileName
                + " - user:  " + SFTPUSER + "- host : " + SFTPHOST + " - port : " + SFTPPORT);
        ChannelSftp channelSftp = null;
        Session session = null;
        Channel channel = null;
        try{
            JSch jsch = new JSch();
            session = jsch.getSession(SFTPUSER,SFTPHOST,SFTPPORT);
            session.setPassword(SFTPPASS);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking","no");
            session.setConfig(config);
            Security.insertProviderAt(new BouncyCastleProvider(),1);
            // Create SFTP Session
            session.connect(timeout);
            // Open SFTP Channel
            channel = session.openChannel("sftp");
            channel.connect(timeout);
            channelSftp = (ChannelSftp)channel;
            recursiveFolderUpload(fileName,filePath,channelSftp);

        } catch(Exception ex){
            log.error(ex.getMessage(), ex);
        } finally{
            if(channelSftp != null){
                channelSftp.disconnect();
            }
            if(channel != null){
                channel.disconnect();
            }
            if(session != null){
                session.disconnect();
            }
        }
        log.info(log.getName() + nomeMeToDo + " END");
    }
    private static void recursiveFolderUpload(String sourcePath,String destinationPath,ChannelSftp channelSftp) throws SftpException, FileNotFoundException {
        String nomeMeToDo = ".recursiveFolderUpload() ";
        log.info(log.getName() + nomeMeToDo + " user: "
                + SecurityContextHolder.getContext().getAuthentication().getName() + "sourcePath : " +sourcePath + " -> destinationPath : " + destinationPath);
        channelSftp.cd("/");
        if(!"".equals(com.fis.fb8.util.StringUtils.nvl(destinationPath,""))){
            String[] folderList = destinationPath.split("/");
            if(folderList.length > 0){
                StringBuilder path = new StringBuilder();
                path.append("/");
                for(int i = 0; i< folderList.length ; i++){
                    if(!"".equals(com.fis.fb8.util.StringUtils.nvl(folderList[i],""))) {
                        path.append(folderList[i]);
                        try {
                            channelSftp.stat(path.toString());
                            if (i == 0) {
                                channelSftp.cd("/" + folderList[i]);
                            }else{
                                channelSftp.cd(folderList[i]);
                            }
                        } catch (Exception e) {
                            log.error(folderList[i] + " not found");
                            if (i == 0) {
                                channelSftp.mkdir(folderList[i]);
                                channelSftp.cd("/" + folderList[i]);
                            }else {
                                channelSftp.mkdir(folderList[i]);
                                channelSftp.cd(folderList[i]);
                            }
                        }
                        path.append("/");
                        if (i == folderList.length - 1) {
                            File sourceFile = new File(sourcePath);
                            if (sourceFile.isFile()) {
                                // copy if it is a file
                                if (!sourceFile.getName().startsWith(".")) {
                                    log.info("copy file : " + sourcePath);
                                    channelSftp.put(new FileInputStream(sourceFile), sourceFile.getName(), ChannelSftp.OVERWRITE);
                                }
                            }
                        }
                    }

                }
            }
        }
        log.info(log.getName() + nomeMeToDo + "END");
    }
}
