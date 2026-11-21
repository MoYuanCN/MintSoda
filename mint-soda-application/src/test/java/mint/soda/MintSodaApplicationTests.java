package mint.soda;

import mint.soda.entity.BgmInfo;
import mint.soda.util.other.BgmUtil;
import mint.soda.util.other.ConfigUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MintSodaApplicationTests {

    @Test
    void bgmTest() {
        ConfigUtil.load();
        BgmInfo bgmInfo = BgmUtil.getBgmInfo("510710");
        System.out.println(bgmInfo);
    }

}
