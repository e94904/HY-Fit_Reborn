package chipsea.bias.v235;

public class CSBiasAPI {
    public static final int CSBIAS_OK = 0;
    public static final int CSBIAS_ERR_WEIGTH = -2;
    public static final int CSBIAS_ERR_HEIGHT = -3;
    public static final int CSBIAS_ERR_AGE = -4;
    public static final int CSBIAS_ERR_SEX = -5;
    public static final int CSBIAS_ERR_IMPEDANCE = -6;
    public static final int CSBIAS_ERR_MODE = -7;
    public static final int CSBIAS_ERR_VCODE = -8;

    static {
        System.loadLibrary("chipsea_bias_v235");
    }

    public static class CSBiasDataV235 {
        public double BFP;
        public double BMC;
        public double BMI;
        public int BMR;
        public double BWP;
        public double FC;
        public int MA;
        public double MC;
        public double PP;
        public int SBC;
        public double SBW;
        public double SLM;
        public double SMM;
        public double VFR;
        public double WC;
    }

    public static class CSBiasV235Resp {
        public int result;
        public CSBiasDataV235 data;
    }

    public static native CSBiasV235Resp cs_bias_v235(
        int sex,
        int height,
        int weightDec,
        int age,
        int impedance,
        int mode,
        int vcode
    );
}
