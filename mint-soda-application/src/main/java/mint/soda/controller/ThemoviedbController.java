package mint.soda.controller;

import mint.soda.annotation.Auth;
import mint.soda.entity.Ani;
import mint.soda.entity.dto.ThemoviedbDTO;
import mint.soda.entity.vo.ThemoviedbVO;
import mint.soda.entity.web.Result;
import mint.soda.entity.web.ResultCode;
import mint.soda.util.other.RenameUtil;
import mint.soda.util.other.TmdbUtils;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import wushuo.tmdb.api.entity.Tmdb;
import wushuo.tmdb.api.entity.TmdbGroup;
import wushuo.tmdb.api.enums.TmdbTypeEnum;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RestController
public class ThemoviedbController extends BaseController {

    @Auth
    @Operation(summary = "获取TMDB标题")
    @PostMapping("/getThemoviedbName")
    public Result<ThemoviedbVO> getThemoviedbName(@RequestBody ThemoviedbDTO dto) {
        Boolean ova = dto.getOva();
        String tmdbId = dto.getTmdbId();
        String title = dto.getTitle();

        Assert.isTrue(
                StrUtil.isNotBlank(tmdbId) || StrUtil.isNotBlank(title),
                "TmdbId 或 标题 不能为空"
        );

        Optional<Tmdb> tmdbOpt;

        if (StrUtil.isNotBlank(tmdbId)) {
            Tmdb tmdb = new Tmdb().setId(tmdbId);
            TmdbTypeEnum tmdbType = ova ? TmdbTypeEnum.MOVIE : TmdbTypeEnum.TV;
            tmdbOpt = TmdbUtils.getTmdb(tmdb, tmdbType);
        } else {
            title = RenameUtil.renameDel(title, false);
            tmdbOpt = ova ? TmdbUtils.getTmdbMovie(title) : TmdbUtils.getTmdbTv(title);
        }

        Assert.isFalse(tmdbOpt.isEmpty(), "未获取到 TMDB");

        Tmdb tmdb = tmdbOpt.get();

        String themoviedbName = TmdbUtils.getFinalName(tmdb);

        ThemoviedbVO themoviedbVO = new ThemoviedbVO()
                .setTmdb(tmdb)
                .setThemoviedbName(themoviedbName);

        Result<ThemoviedbVO> result = new Result<ThemoviedbVO>()
                .setCode(ResultCode.HTTP_OK)
                .setMessage("获取 TMDB 成功")
                .setData(themoviedbVO);
        if (StrUtil.isBlank(themoviedbName)) {
            result.setCode(ResultCode.HTTP_INTERNAL_ERROR)
                    .setMessage("获取 TMDB 失败");
        }
        return result;
    }

    @Auth
    @Operation(summary = "获取TMDB剧集组")
    @PostMapping("/getThemoviedbGroup")
    public Result<List<TmdbGroup>> getThemoviedbGroup(@RequestBody Ani ani) {
        Tmdb tmdb = ani.getTmdb();
        Objects.requireNonNull(tmdb, "tmdb is null");
        Assert.notBlank(tmdb.getId(), "tmdb is null");
        List<TmdbGroup> tmdbGroup = TmdbUtils.getTmdbGroup(tmdb);
        return Result.success(tmdbGroup);
    }
}
