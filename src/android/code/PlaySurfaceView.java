package fxp.plugin.video;

import android.annotation.SuppressLint;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.hikvision.netsdk.HCNetSDK;
import com.hikvision.netsdk.NET_DVR_PREVIEWINFO;
import com.hikvision.netsdk.RealPlayCallBack;

import org.MediaPlayer.PlayM4.Player;

/**
 * 视频 SurfaceView
 *
 * @author fxp
 * @mail 850899969@qq.com
 * @date 2018/1/10 下午7:01
 */
@SuppressLint({"NewApi"})
public class PlaySurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private final String TAG = "PlaySurfaceView";

    private boolean m_bSurfaceCreated = false;

    private int m_iHeight = 0;

    private int m_iPort = -1;

    public int m_iPreviewHandle = -1;

    private int m_iWidth = 0;

    public PlaySurfaceView(MonitorVedioActivity monitorVedioActivity) {
        super(monitorVedioActivity);
        getHolder().addCallback(this);
    }

    protected void onMeasure(int paramInt1, int paramInt2) {
        super.setMeasuredDimension(-1 + this.m_iWidth, -1 + this.m_iHeight);
    }

    public void setParam(int screenWidth, int screenHeight) {
        this.m_iWidth = (screenWidth / 2);
        this.m_iHeight = (3 * this.m_iWidth / 4);
    }

    public void setViewSize(int width, int height) {
        this.m_iWidth = width;
        this.m_iHeight = height;
    }

    public int getCurHeight() {
        return this.m_iHeight;
    }

    public int getCurWidth() {
        return this.m_iWidth;
    }

    /**
     * 获取实时流音视频数据
     * @return  码流数据回调函数
     */
    private RealPlayCallBack getRealPlayerCbf() {
        return new RealPlayCallBack() {
            public void fRealDataCallBack(int paramAnonymousInt1, int paramAnonymousInt2, byte[] paramAnonymousArrayOfByte, int paramAnonymousInt3) {
                PlaySurfaceView.this.processRealData(1, paramAnonymousInt2, paramAnonymousArrayOfByte, paramAnonymousInt3, 0);
            }
        };
    }

    /**
     * 通过播放库解码显示到SurfaceView
     *
     * @param iPlayViewNo   当前的预览句柄
     * @param iDataType     数据类型
     * @param pDataBuffer   存放数据的缓冲区指针
     * @param iDataSize     缓冲区大小
     * @param iStreamMode   实时流模式
     */
    private void processRealData(int iPlayViewNo, int iDataType, byte[] pDataBuffer, int iDataSize, int iStreamMode) {
        if (HCNetSDK.NET_DVR_SYSHEAD == iDataType) {
            if (m_iPort >= 0) {
                return;
            }
            m_iPort = Player.getInstance().getPort();
            if (m_iPort == -1) {
                Log.e(TAG, "getPort is failed with: " + Player.getInstance().getLastError(m_iPort));
                return;
            }
            Log.i(TAG, "getPort succ with: " + m_iPort);
            if (iDataSize > 0) {
                if (!Player.getInstance().setStreamOpenMode(m_iPort, iStreamMode)) //set stream mode
                {
                    Log.e(TAG, "setStreamOpenMode failed");
                    return;
                }
                if (!Player.getInstance().openStream(m_iPort, pDataBuffer, iDataSize, 2 * 1024 * 1024)) //open stream
                {
                    Log.e(TAG, "openStream failed");
                    return;
                }
                while (!m_bSurfaceCreated) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    Log.i(TAG, "wait 100 for surface, handle:" + iPlayViewNo);
                }

                if (!Player.getInstance().play(m_iPort, getHolder())) {
                    Log.e(TAG, "play failed,error:" + Player.getInstance().getLastError(m_iPort));
                    return;
                }
                if (!Player.getInstance().playSound(m_iPort)) {
                    Log.e(TAG, "playSound failed with error code:" + Player.getInstance().getLastError(m_iPort));
                    return;
                }
            }
        } else {
            if (!Player.getInstance().inputData(m_iPort, pDataBuffer, iDataSize)) {
                Log.e(TAG, "inputData failed with: " + Player.getInstance().getLastError(m_iPort));
            }
        }
    }

    private void stopPlayer() {
        Player.getInstance().stopSound();
        if (!Player.getInstance().stop(this.m_iPort)) {
            Log.e(TAG, "stop is failed!");
            return;
        }
        if (!Player.getInstance().closeStream(this.m_iPort)) {
            Log.e(TAG, "closeStream is failed!");
            return;
        }
        if (Player.getInstance().freePort(this.m_iPort)) {
            this.m_iPort = -1;
            return;
        }
        Log.e(TAG, "freePort is failed!" + this.m_iPort);
    }

    public void startPreview(int iLogId, int chanNum) {
        RealPlayCallBack realPlayCallBack = getRealPlayerCbf();
        if (realPlayCallBack == null) {
            Log.e(TAG, "fRealDataCallBack object is failed!");
            return;
        }
        Log.i(TAG, "preview channel:" + chanNum);
        NET_DVR_PREVIEWINFO netDVRPreviewInfo = new NET_DVR_PREVIEWINFO();
        // 通道号，模拟通道号从1开始，数字通道号从33开始，具体取值在登录接口返回
        netDVRPreviewInfo.lChannel = chanNum;
        // 码流类型
        netDVRPreviewInfo.dwStreamType = 1;
        // 连接方式，0-TCP方式，1-UDP方式，2-多播方式，3-RTP方式，4-RTP/RTSP，5-RSTP/HTTP
        // previewInfo.dwLinkMode = 5;
        // 0-非阻塞取流，1-阻塞取流
        netDVRPreviewInfo.bBlocked = 1;
        // 实时预览，返回值-1表示失败
        this.m_iPreviewHandle = HCNetSDK.getInstance().NET_DVR_RealPlay_V40(iLogId, netDVRPreviewInfo, realPlayCallBack);

        if (m_iPreviewHandle < 0) {
            Log.e(TAG, "NET_DVR_RealPlay is failed!Err:" + HCNetSDK.getInstance().NET_DVR_GetLastError());
        }
    }

    public void stopPreview() {
        HCNetSDK.getInstance().NET_DVR_StopRealPlay(this.m_iPreviewHandle);
        stopPlayer();
    }

    public void surfaceChanged(SurfaceHolder paramSurfaceHolder, int paramInt1, int paramInt2, int paramInt3) {
        System.out.println("surfaceChanged");
    }

    public void surfaceCreated(SurfaceHolder paramSurfaceHolder) {
        this.m_bSurfaceCreated = true;
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        if (-1 == m_iPort) {
            return;
        }
        Surface surface = paramSurfaceHolder.getSurface();
        if (true == surface.isValid()) {
            if (false == Player.getInstance().setVideoWindow(m_iPort, 0, paramSurfaceHolder)) {
                Log.e(TAG, "Player setVideoWindow failed!");
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        // TODO Auto-generated method stub
        m_bSurfaceCreated = false;
        if (-1 == m_iPort) {
            return;
        }
        if (true == arg0.getSurface().isValid()) {
            if (false == Player.getInstance().setVideoWindow(m_iPort, 0, null)) {
                Log.e(TAG, "Player setVideoWindow failed!");
            }
        }
    }
}