package ParkingCloud.AD;

import ParkingCloud.Error;
import ParkingCloud.*;
import ParkingCloud.ParkingLot.*;
import ParkingCloud.Storage.Redis.Action.*;
import org.apache.commons.lang.builder.*;
import org.springframework.stereotype.*;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import redis.clients.jedis.*;

import java.util.*;

@Controller
public class ADController {

    // 测试环境
    public static final String TEST_APPID = "abagdwGPn3LoNPu05T";
    public static final String TEST_SECRET = "z28unHOmAj3T3sCUDU3d732woIE298xp4";
    public static final String TEST_URL = "http://anbo-test.anbokeji.net";
    // 生产环境
    public static final String PROD_URL = "http://anbo.anbojuhe.com";
    public static final String PROD_APPID = "abI8bhNqn7Jd7fAn6o";
    public static final String PROD_SECRET = "zUVCS4E97nMSgrIzEwOLijiOat9079uQ5";

    /**
     * 泊车场信息初始化接口
     * @param env
     * @return
     */
    @RequestMapping(value = "/ad/abinit", method = RequestMethod.GET)
    @ResponseBody
    public ReturnResult GetList(@RequestParam(defaultValue = "prod") String env) {
        Loger.Log.warn(String.format("---初始化安泊车场信息初始化接口开始，当前运行环境为：%s---", env));

        // 区分测试和生产环境
        String url = env.equals("prod") ? PROD_URL : TEST_URL;
        String appId = env.equals("prod") ? PROD_APPID : TEST_APPID;
        String secret = env.equals("prod") ? PROD_SECRET : TEST_SECRET;

        // 查询所有停车场
        List<ParkingLot> parkingLots = ParkingLot.LoadList();
        Loger.Log.warn(String.format("获取到所有停车场数量：%d。", parkingLots.size()));


        String apiUrl = url + "/ab/v2/init/parkInfo";
        SortedMap<String, String> params = new TreeMap<>();
        params.put("appid", appId);
        params.put("adPosIds", "3#5");
        params.put("medium", "1");
        params.put("conceStr", String.valueOf(new Random().nextInt(1000000000)));

        AnBoResultDTO resultDTO;
        RestTemplate restTemplate = new RestTemplate();
        ReturnResult result = new ReturnResult();
        for (ParkingLot parkingLot : parkingLots) {
            String parkName = parkingLot.Name;
            params.put("parkName", parkName);
            String cityName = parkingLot.City;
            String cityId = CityIdEnum.getCodeByCityName(cityName);
            if (cityName.contains("区") && !parkName.equals("金普新区") && !parkName.equals("两江新区")) {
                cityId = CityIdEnum.getCodeByCityName(parkingLot.Province);
            }
            params.put("cityId", cityId);
            params.put("sign", sign(params, secret));
            Loger.Log.warn(String.format("准备调用安泊车场信息初始化接口，车场名称：%s---", parkName));
            resultDTO = restTemplate.postForObject(apiUrl, params, AnBoResultDTO.class);
            if (resultDTO.getErrorCode().equals("0")) {
                Map data = resultDTO.getData();
                Loger.Log.warn(String.format("调用安泊车场信息初始化接口成功，返回信息：%s---", data));
                saveParkId(parkName, data.get("parkId").toString());
            } else {
                result.Result = Error.INVALID_STATE;
                Loger.Log.error(String.format("调用安泊车场信息初始化接口失败，返回信息：%s。", ToStringBuilder.reflectionToString(resultDTO, ToStringStyle.SHORT_PREFIX_STYLE)));
            }
        }
        Loger.Log.warn("---初始化安泊车场信息初始化接口结束。---");
        return result;
    }

    private void saveParkId(String parkName, String parkId) {
        ActionResult<Integer> result = Action.DoAction(new Action<Integer>() {
            @Override
            public Integer OnAction(Jedis Jedis, Object parkId, Object UserPara2, Object UserPara3) {
                Map<String, String> Map = new Hashtable<>();
                Map.put(parkName, parkId.toString());
                Jedis.hmset("AbAdParkInfo", Map);
                return Error.SUCCESS;
            }

        }, parkId, null, null);

        //判断是否成功
        if (!(Error.SUCCESS == result.Result) && (Error.SUCCESS == result.Value)) {
            Loger.Log.error(String.format("调用安泊车场信息初始化接口失败，redis存储parkId失败：parkName: %s, parkId: %s。", parkName, parkId));
        }
    }

    /**
     * 生成签名
     */
    public static String sign(Map<String, String> data, String apiSecret) {
        Map<String, String> sortedParams = new TreeMap<>(data);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (!"sign".equals(k) && null != v && !"".equals(v)) {
                sb.append(k).append("=").append(v).append("&");
            }
        }
        sb.append("key=").append(apiSecret);
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes());
    }
}
