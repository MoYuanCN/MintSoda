package ani.rss;

import ani.rss.entity.BgmInfo;
import ani.rss.util.other.BgmUtil;
import ani.rss.util.other.ConfigUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AniRssApplicationTests {

    @Test
    void bgmTest() {
        ConfigUtil.load();
        BgmInfo bgmInfo = BgmUtil.getBgmInfo("510710");
        System.out.println(bgmInfo);
    }

}
