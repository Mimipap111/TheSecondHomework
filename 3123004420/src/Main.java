import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    // 定义文本处理模式
    private static final Pattern TEXT_TOKEN_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5a-zA-Z0-9]+");
    private static final int SIMHASH_BIT_LENGTH = 64;
    private static final long HASH_SEED = 5381L;

    public static void main(String[] commandArgs) {
        if (commandArgs.length == 1 && "verify".equals(commandArgs[0])) {
            executeTestScenarios();
            return;
        }

        if (!checkArguments(commandArgs)) {
            return;
        }

        String sourceDocPath = commandArgs[0];
        String targetDocPath = commandArgs[1];
        String outputPath = commandArgs[2];

        long beginTime = System.currentTimeMillis();
        Date startTimestamp = new Date(beginTime);

        try {
            verifyFileAvailability(sourceDocPath);
            verifyFileAvailability(targetDocPath);

            String sourceContent = extractFileContent(sourceDocPath);
            String targetContent = extractFileContent(targetDocPath);

            long sourceFingerprint = generateDocumentFingerprint(sourceContent);
            long targetFingerprint = generateDocumentFingerprint(targetContent);

            int differenceScore = computeDifferenceScore(sourceFingerprint, targetFingerprint);
            double similarityScore = 1.0 - (double) differenceScore / SIMHASH_BIT_LENGTH;

            long endTime = System.currentTimeMillis();
            long processingDuration = endTime - beginTime;

            saveAnalysisResult(outputPath, differenceScore, similarityScore,
                    startTimestamp, new Date(endTime), processingDuration,
                    sourceDocPath, targetDocPath);

            System.out.println("分析完成，结果保存至: " + outputPath);
            System.out.printf("相似度: %.2f%%\n", similarityScore * 100);
            System.out.println("处理耗时: " + processingDuration + " 毫秒");

        } catch (FileNotFoundException fnfe) {
            System.err.println("文件未找到错误: " + fnfe.getMessage());
        } catch (UnsupportedEncodingException uee) {
            System.err.println("编码不支持: " + uee.getMessage());
        } catch (IOException ioe) {
            System.err.println("文件处理异常: " + ioe.getMessage());
        } catch (Exception e) {
            System.err.println("未预期错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean checkArguments(String[] args) {
        if (args.length != 3) {
            System.err.println("参数数量不正确！");
            System.err.println("正确用法: java Main <原文路径> <待检路径> <输出路径>");
            System.err.println("测试模式: java Main verify");
            return false;
        }

        for (int i = 0; i < 3; i++) {
            if (args[i] == null || args[i].trim().isEmpty()) {
                System.err.println("错误: 第 " + (i+1) + " 个参数不能为空");
                return false;
            }
        }

        return true;
    }

    private static void verifyFileAvailability(String filePath) throws FileNotFoundException {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("无法访问文件: " + filePath);
        }
    }

    private static String extractFileContent(String filePath) throws IOException {
        Charset[] supportedCharsets = {
                StandardCharsets.UTF_8,
                Charset.forName("GBK"),
                Charset.forName("GB2312"),
                StandardCharsets.ISO_8859_1,
                StandardCharsets.UTF_16
        };

        byte[] fileData = Files.readAllBytes(Paths.get(filePath));

        for (Charset charset : supportedCharsets) {
            try {
                return new String(fileData, charset);
            } catch (Exception e) {
                continue;
            }
        }

        throw new UnsupportedEncodingException("无法识别文件编码: " + filePath);
    }

    private static long generateDocumentFingerprint(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0L;
        }

        List<String> tokens = tokenizeContent(content);
        if (tokens.isEmpty()) {
            return 0L;
        }

        Map<String, Integer> tokenWeights = calculateTokenWeights(tokens);
        int[] fingerprintVector = new int[SIMHASH_BIT_LENGTH];

        for (Map.Entry<String, Integer> entry : tokenWeights.entrySet()) {
            long tokenHash = computeTokenHash(entry.getKey());
            int weight = entry.getValue();

            for (int position = 0; position < SIMHASH_BIT_LENGTH; position++) {
                long bitValue = (tokenHash >> (SIMHASH_BIT_LENGTH - 1 - position)) & 1;
                fingerprintVector[position] += (bitValue == 1) ? weight : -weight;
            }
        }

        long documentFingerprint = 0L;
        for (int position = 0; position < SIMHASH_BIT_LENGTH; position++) {
            if (fingerprintVector[position] > 0) {
                documentFingerprint |= (1L << (SIMHASH_BIT_LENGTH - 1 - position));
            }
        }

        return documentFingerprint;
    }

    private static List<String> tokenizeContent(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return tokens;
        }

        Matcher tokenMatcher = TEXT_TOKEN_PATTERN.matcher(text);
        while (tokenMatcher.find()) {
            tokens.add(tokenMatcher.group().toLowerCase());
        }

        return tokens;
    }

    private static Map<String, Integer> calculateTokenWeights(List<String> tokens) {
        Map<String, Integer> weightMap = new HashMap<>();
        for (String token : tokens) {
            weightMap.put(token, weightMap.getOrDefault(token, 0) + 1);
        }
        return weightMap;
    }

    private static long computeTokenHash(String token) {
        if (token == null || token.isEmpty()) {
            return 0L;
        }

        long hashValue = HASH_SEED;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            hashValue = ((hashValue << 5) + hashValue) + c;
        }

        return hashValue;
    }

    private static int computeDifferenceScore(long hash1, long hash2) {
        long xorResult = hash1 ^ hash2;
        int differenceCount = 0;

        while (xorResult != 0) {
            differenceCount++;
            xorResult = xorResult & (xorResult - 1);
        }

        return differenceCount;
    }

    private static void saveAnalysisResult(String outputPath, int differenceScore,
                                           double similarityScore, Date startTime,
                                           Date endTime, long duration,
                                           String sourcePath, String targetPath)
            throws IOException {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        File resultFile = new File(outputPath);

        if (!resultFile.getParentFile().exists()) {
            resultFile.getParentFile().mkdirs();
        }

        boolean appendMode = resultFile.exists();

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(outputPath, true), StandardCharsets.UTF_8))) {

            if (appendMode) {
                writer.println();
                writer.println("========================================");
                writer.println("文档相似度分析报告");
                writer.println("========================================");
                writer.println();
            }

            writer.println("分析时间: " + dateFormatter.format(startTime) + " - " +
                    dateFormatter.format(endTime));
            writer.println("处理时长: " + duration + " 毫秒");
            writer.println("源文档: " + sourcePath);
            writer.println("目标文档: " + targetPath);
            writer.println("差异分数: " + differenceScore);
            writer.printf("相似度: %.2f%%\n", similarityScore * 100);

            String similarityLevel;
            if (similarityScore >= 0.8) {
                similarityLevel = "高度相似，可能存在抄袭";
            } else if (similarityScore >= 0.5) {
                similarityLevel = "中度相似，建议进一步检查";
            } else if (similarityScore >= 0.3) {
                similarityLevel = "轻度相似，可能存在借鉴";
            } else {
                similarityLevel = "相似度较低，原创性较高";
            }

            writer.println("评估结果: " + similarityLevel);
            writer.println("----------------------------------------");
        }
    }

    private static void executeTestScenarios() {
        System.out.println("执行测试案例...\n");

        String[][] testCases = {
                {"testfiles/source_doc.txt", "testfiles/target_doc.txt", "results/analysis.txt"},
                {"testfiles/doc1.txt", "testfiles/doc2.txt", "results/analysis.txt"},
                {"testfiles/empty.txt", "testfiles/empty.txt", "results/analysis.txt"}
        };

        for (int i = 0; i < testCases.length; i++) {
            System.out.println("执行测试 " + (i + 1) + "/" + testCases.length);
            try {
                processTestcase(testCases[i][0], testCases[i][1], testCases[i][2]);
            } catch (Exception e) {
                System.err.println("测试 " + (i + 1) + " 出错: " + e.getMessage());
            }
            System.out.println("------------------------------");
        }

        System.out.println("测试执行完成");
    }

    private static void processTestcase(String sourcePath, String targetPath, String outputPath) {
        try {
            verifyFileAvailability(sourcePath);
            verifyFileAvailability(targetPath);

            String sourceContent = extractFileContent(sourcePath);
            String targetContent = extractFileContent(targetPath);

            long sourceFingerprint = generateDocumentFingerprint(sourceContent);
            long targetFingerprint = generateDocumentFingerprint(targetContent);

            int differenceScore = computeDifferenceScore(sourceFingerprint, targetFingerprint);
            double similarityScore = 1.0 - (double) differenceScore / SIMHASH_BIT_LENGTH;

            System.out.println("测试结果 - 相似度: " + String.format("%.2f%%", similarityScore * 100));

        } catch (Exception e) {
            System.err.println("测试处理失败: " + e.getMessage());
        }
    }
}