package com.chuan.module.resume.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 简历结构化内容（前后端交互与存储的统一模型）。
 */
@Data
public class ResumeContent implements Serializable {

    /** 证件照（data URI，如 data:image/jpeg;base64,...） */
    private String photo;

    private BasicInfo basic = new BasicInfo();

    private List<Education> educations = new ArrayList<>();

    private List<Internship> internships = new ArrayList<>();

    private List<CampusActivity> campusActivities = new ArrayList<>();

    private List<Certificate> certificates = new ArrayList<>();

    /** 自我评价 */
    private String selfEvaluation;

    /** 求职意向（目标岗位 / 期望薪资 / 工作城市） */
    private CareerGoal careerGoal = new CareerGoal();

    /** 基本信息 */
    @Data
    public static class BasicInfo implements Serializable {
        private String name;
        private String ethnicity;
        private String phone;
        private String email;
        /** 省市区（级联选择结果，按省/市/区顺序存储地名） */
        private List<String> region = new ArrayList<>();
        /** 详细地址 */
        private String address;
        /** 出生年月（yyyy-MM-dd） */
        private String birthDate;
        /** 身高（cm） */
        private Integer height;
        private String politicalStatus;
    }

    /** 教育背景 */
    @Data
    public static class Education implements Serializable {
        private String school;
        private String degree;
        /** 毕业时间（yyyy-MM-dd） */
        private String graduateDate;
    }

    /** 实习经历 */
    @Data
    public static class Internship implements Serializable {
        private String company;
        private String position;
        private String startDate;
        private String endDate;
        private String description;
    }

    /** 校园经历 */
    @Data
    public static class CampusActivity implements Serializable {
        private String organization;
        private String position;
        private String startDate;
        private String endDate;
        private String duty;
    }

    /** 专业证书 */
    @Data
    public static class Certificate implements Serializable {
        private String name;
        /** 获得时间（yyyy-MM-dd） */
        private String obtainDate;
        private String issuer;
    }

    /** 求职意向 */
    @Data
    public static class CareerGoal implements Serializable {
        /** 目标岗位 */
        private String targetPosition;
        /** 期望薪资 */
        private String expectedSalary;
        /** 工作城市 */
        private String workCity;
    }
}