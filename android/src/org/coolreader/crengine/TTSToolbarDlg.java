package org.coolreader.crengine;

import java.util.HashMap;

import org.coolreader.CoolReader;
import org.coolreader.R;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.s_trace.motion_watchdog.HandlerThread;
import com.s_trace.motion_watchdog.MotionWatchdogHandler;

public class TTSToolbarDlg implements TTS.OnUtteranceCompletedListener {
	PopupWindow mWindow;
	View mAnchor;
	CoolReader mCoolReader;
	ReaderView mReaderView;
	View mPanel;
	TTS mTTS;
	ImageButton playPauseButton;
	ImageView ivVolDown;
	ImageView ivVolUp;
	ImageView ivFreqDown;
	ImageView ivFreqUp;
	SeekBar sbSpeed;
	SeekBar sbVolume;
	private HandlerThread mMotionWatchdog;

	static public TTSToolbarDlg showDialog( CoolReader coolReader, ReaderView readerView, TTS tts)
	{
		TTSToolbarDlg dlg = new TTSToolbarDlg(coolReader, readerView, tts);
		//dlg.mWindow.update(dlg.mAnchor, width, height)
		Log.d("cr3", "popup: " + dlg.mWindow.getWidth() + "x" + dlg.mWindow.getHeight());
		//dlg.update();
		//dlg.showAtLocation(readerView, Gravity.LEFT|Gravity.TOP, readerView.getLeft()+50, readerView.getTop()+50);
		//dlg.showAsDropDown(readerView);
		//dlg.update();
		return dlg;
	}
	
	private Runnable onCloseListener;
	public void setOnCloseListener(Runnable handler) {
		onCloseListener = handler;
	}

	private boolean closed; 
	public void stopAndClose() {
		if (closed)
			return;
		isSpeaking = false;
		closed = true;
		BackgroundThread.instance().executeGUI(new Runnable() {
			@Override
			public void run() {
				stop();
				restoreReaderMode();
				mReaderView.clearSelection();
				if (onCloseListener != null)
					onCloseListener.run();
				if ( mWindow.isShowing() )
					mWindow.dismiss();
				mReaderView.save();
			}
		});
	}
	
	private boolean changedPageMode;
	private void setReaderMode()
	{
		String oldViewSetting = mReaderView.getSetting( ReaderView.PROP_PAGE_VIEW_MODE );
		if ( "1".equals(oldViewSetting) ) {
			changedPageMode = true;
			mReaderView.setSetting(ReaderView.PROP_PAGE_VIEW_MODE, "0");
			mReaderView.setSetting(ReaderView.PROP_PAGE_VIEW_MODE_AUTOCHANGED, "1");
		}
		moveSelection( ReaderCommand.DCMD_SELECT_FIRST_SENTENCE );
	}
	
	private void restoreReaderMode()
	{
		if ( changedPageMode ) {
			mReaderView.setSetting(ReaderView.PROP_PAGE_VIEW_MODE, "1");
			mReaderView.setSetting(ReaderView.PROP_PAGE_VIEW_MODE_AUTOCHANGED, "0");
		}
	}
	
	private Selection currentSelection;
	
	private void moveSelection( ReaderCommand cmd )
	{
		mReaderView.moveSelection(cmd, 0, new ReaderView.MoveSelectionCallback() {
			
			@Override
			public void onNewSelection(Selection selection) {
				Log.d("cr3", "onNewSelection: " + selection.text);
				curTime = System.currentTimeMillis();
				if (curTime - lastSaveCurPosTime > mReaderView.getDefSavePositionInterval()) {
					lastSaveCurPosTime = curTime;
					try {
						final Bookmark bmk = mReaderView.getCurrentPositionBookmark();
						if (bmk != null) mReaderView.savePositionBookmark(bmk);
					} catch (Exception e) {
						L.e("couldn't save current position");
					}
				}
				currentSelection = selection;
				if ( isSpeaking )
					say( currentSelection );
			}
			
			@Override
			public void onFail() {
				Log.d("cr3", "fail()");
				stop();
				//currentSelection = null;
			}
		});
	}
	
	private void say( Selection selection ) {
		HashMap<String, String> params = new HashMap<String, String>();
		params.put(TTS.KEY_PARAM_UTTERANCE_ID, "cr3UtteranceId");
		mTTS.speak(selection.text, TTS.QUEUE_ADD, params);
	}
	
	private void start() {
		if ( currentSelection==null )
			return;
		startMotionWatchdog();
		isSpeaking = true;
		say( currentSelection );
	}

	private void startMotionWatchdog(){
		String TAG = "MotionWatchdog";
		Log.d(TAG, "startMotionWatchdog() enter");

		Properties settings = mReaderView.getSettings();
		int timeout = settings.getInt(ReaderView.PROP_APP_MOTION_TIMEOUT, 0);
		if (timeout == 0) {
			Log.d(TAG, "startMotionWatchdog() early exit - timeout is 0");
			return;
		}
		timeout = timeout * 60 * 1000; // Convert minutes to msecs

		mMotionWatchdog = new HandlerThread("MotionWatchdog");
		mMotionWatchdog.start();
		new MotionWatchdogHandler(this, mCoolReader, mMotionWatchdog, timeout);
		Log.d(TAG, "startMotionWatchdog() exit");
	}
	
	private boolean isSpeaking;
	private long curTime = System.currentTimeMillis();
	private long lastSaveCurPosTime = System.currentTimeMillis();

	private void stop() {
		isSpeaking = false;
		if ( mTTS.isSpeaking() ) {
			mTTS.stop();
		}
		if (mMotionWatchdog != null) {
			mMotionWatchdog.interrupt();
		}
	}

	public void pause() {
		if (isSpeaking)
			toggleStartStop();
	}
	
	private void toggleStartStop() {
		if ( isSpeaking ) {
			playPauseButton.setImageResource(
					Utils.resolveResourceIdByAttr(mCoolReader, R.attr.attr_ic_media_play, R.drawable.ic_media_play)
					//R.drawable.ic_media_play
			);
			stop();
		} else {
			playPauseButton.setImageResource(
					Utils.resolveResourceIdByAttr(mCoolReader, R.attr.attr_ic_media_pause, R.drawable.ic_media_pause)
					//R.drawable.ic_media_pause
			);
			start();
		}
	}
	
	@Override
	public void onUtteranceCompleted(String utteranceId) {
		Log.d("cr3", "onUtteranceCompleted " + utteranceId);
		if ( isSpeaking )
			moveSelection( ReaderCommand.DCMD_SELECT_NEXT_SENTENCE );
	}

	public TTSToolbarDlg( CoolReader coolReader, ReaderView readerView, TTS tts )
	{
		mCoolReader = coolReader;
		mReaderView = readerView;
		mAnchor = readerView.getSurface();
		mTTS = tts;
		mTTS.setOnUtteranceCompletedListener(this);

		int colorGrayC;
		int colorGray;
		TypedArray a = mCoolReader.getTheme().obtainStyledAttributes(new int[]
				{R.attr.colorThemeGray2Contrast, R.attr.colorThemeGray2});
		colorGrayC = a.getColor(0, Color.GRAY);
		colorGray = a.getColor(1, Color.GRAY);
		a.recycle();

		ColorDrawable c = new ColorDrawable(colorGrayC);
		c.setAlpha(130);

		View panel = (LayoutInflater.from(coolReader.getApplicationContext()).inflate(R.layout.tts_toolbar, null));
		playPauseButton = (ImageButton)panel.findViewById(R.id.tts_play_pause);
		playPauseButton.setImageResource(
				Utils.resolveResourceIdByAttr(mCoolReader, R.attr.attr_ic_media_play, R.drawable.ic_media_play)
				//R.drawable.ic_media_play
		);
		playPauseButton.setBackgroundDrawable(c);
		//panel.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		panel.measure(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		
		//mReaderView.getS
		
		mWindow = new PopupWindow( mAnchor.getContext() );
//		mWindow.setFocusable(true);
//		mWindow.setTouchable(true);
//		mWindow.setOutsideTouchable(true);
		mWindow.setBackgroundDrawable(new BitmapDrawable());
//		mWindow.setTouchInterceptor(new OnTouchListener() {
//			@Override
//			public boolean onTouch(View v, MotionEvent event) {
////				if ( event.getAction()==MotionEvent.ACTION_OUTSIDE ) {
////					stopAndClose();
////					return true;
////				}
//				return true;
//			}
//		});
		//super(panel);
		mPanel = panel;
		mPanel.findViewById(R.id.tts_play_pause).setBackgroundDrawable(c);
		mPanel.findViewById(R.id.tts_play_pause).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				toggleStartStop();
			}
		});
		mPanel.findViewById(R.id.tts_play_pause).setOnLongClickListener(new View.OnLongClickListener() {
			public boolean onLongClick(View v) {
				mCoolReader.tts = null;
				mCoolReader.ttsInitialized = false;
				mCoolReader.showToast("Re-initializing TTS");
				return true;
			}
		});
		mPanel.findViewById(R.id.tts_back).setBackgroundDrawable(c);
		mPanel.findViewById(R.id.tts_back).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if ( isSpeaking ) {
					isSpeaking = false;
					mTTS.stop();
					isSpeaking = true;
				}
				moveSelection( ReaderCommand.DCMD_SELECT_PREV_SENTENCE );
			}
		});
		mPanel.findViewById(R.id.tts_forward).setBackgroundDrawable(c);
		mPanel.findViewById(R.id.tts_forward).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if ( isSpeaking ) {
					isSpeaking = false;
					mTTS.stop();
					isSpeaking = true;
				}
				moveSelection( ReaderCommand.DCMD_SELECT_NEXT_SENTENCE );
			}
		});
		mPanel.findViewById(R.id.tts_stop).setBackgroundDrawable(c);
		mPanel.findViewById(R.id.tts_stop).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				stopAndClose();
			}
		});
		mPanel.setFocusable(true);
		mPanel.setEnabled(true);
		mPanel.setOnKeyListener( new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ( event.getAction()==KeyEvent.ACTION_UP ) {
					switch ( keyCode ) {
					case KeyEvent.KEYCODE_VOLUME_DOWN:
					case KeyEvent.KEYCODE_VOLUME_UP:
						return true;
					case KeyEvent.KEYCODE_BACK:
						stopAndClose();
						return true;
//					case KeyEvent.KEYCODE_DPAD_LEFT:
//					case KeyEvent.KEYCODE_DPAD_UP:
//						//mReaderView.findNext(pattern, true, caseInsensitive);
//						return true;
//					case KeyEvent.KEYCODE_DPAD_RIGHT:
//					case KeyEvent.KEYCODE_DPAD_DOWN:
//						//mReaderView.findNext(pattern, false, caseInsensitive);
//						return true;
					}
				} else if ( event.getAction()==KeyEvent.ACTION_DOWN ) {
					switch ( keyCode ) {
					case KeyEvent.KEYCODE_VOLUME_DOWN: {
						int p = sbVolume.getProgress() - 5;
						if ( p<0 )
							p = 0;
						sbVolume.setProgress(p);
						return true;
					}
					case KeyEvent.KEYCODE_VOLUME_UP:
						int p = sbVolume.getProgress() + 5;
						if ( p>100 )
							p = 100;
						sbVolume.setProgress(p);
						return true;
					}
					if ( keyCode == KeyEvent.KEYCODE_BACK) {
						return true;
					}
				}
				return false;
			}
			
		});

		mWindow.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss() {
				if ( !closed )
					stopAndClose();
			}
		});
		
		mWindow.setBackgroundDrawable(new BitmapDrawable());
		//mWindow.setAnimationStyle(android.R.style.Animation_Toast);
		//mWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
		mWindow.setWidth(WindowManager.LayoutParams.FILL_PARENT);
		mWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
//		setWidth(panel.getWidth());
//		setHeight(panel.getHeight());

		mWindow.setFocusable(true);
		mWindow.setTouchable(true);
		mWindow.setOutsideTouchable(true);
		panel.setBackgroundColor(Color.argb(170, Color.red(colorGray),Color.green(colorGray),Color.blue(colorGray)));
		mWindow.setContentView(panel);
		
		
		int [] location = new int[2];
		mAnchor.getLocationOnScreen(location);
		//mWindow.update(location[0], location[1], mPanel.getWidth(), mPanel.getHeight() );
		//mWindow.setWidth(mPanel.getWidth());
		//mWindow.setHeight(mPanel.getHeight());

		mWindow.showAtLocation(mAnchor, Gravity.TOP | Gravity.CENTER_HORIZONTAL, location[0], location[1] + mAnchor.getHeight() - mPanel.getHeight());
//		if ( mWindow.isShowing() )
//			mWindow.update(mAnchor, 50, 50);
		//dlg.mWindow.showAsDropDown(dlg.mAnchor);
		
		setReaderMode();

		// setup speed && volume seek bars
		sbSpeed = (SeekBar)mPanel.findViewById(R.id.tts_sb_speed);
		sbVolume = (SeekBar)mPanel.findViewById(R.id.tts_sb_volume);

		ivVolDown = (ImageView)mPanel.findViewById(R.id.btn_vol_down);
		ivVolUp = (ImageView)mPanel.findViewById(R.id.btn_vol_up);
		ivFreqDown = (ImageView)mPanel.findViewById(R.id.btn_freq_down);
		ivFreqUp = (ImageView)mPanel.findViewById(R.id.btn_freq_up);
		ivVolDown.setBackgroundDrawable(c);
		ivVolUp.setBackgroundDrawable(c);
		ivFreqDown.setBackgroundDrawable(c);
		ivFreqUp.setBackgroundDrawable(c);

		sbSpeed.setMax(100);
		sbSpeed.setProgress(50);
		sbVolume.setMax(100);
		sbVolume.setProgress(mCoolReader.getVolume());

		ivFreqDown.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				int progress = sbSpeed.getProgress();
				if (progress>10) progress=progress-10; else progress = 0;
				sbSpeed.setProgress(progress);
				float rate = 1.0f;
				if ( progress<50 )
					rate = 0.3f + 0.7f * progress / 50f;
				else
					rate = 1.0f + 2.5f * (progress-50) / 50f;
				mTTS.setSpeechRate(rate);
			}
		});
		ivFreqUp.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				int progress = sbSpeed.getProgress();
				if (progress<100) progress=progress+10; else progress = 100;
				sbSpeed.setProgress(progress);
				float rate = 1.0f;
				if ( progress<50 )
					rate = 0.3f + 0.7f * progress / 50f;
				else
					rate = 1.0f + 2.5f * (progress-50) / 50f;
				mTTS.setSpeechRate(rate);
			}
		});

		sbSpeed.setOnSeekBarChangeListener( new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				float rate = 1.0f;
				if ( progress<50 )
					rate = 0.3f + 0.7f * progress / 50f;
				else
					rate = 1.0f + 2.5f * (progress-50) / 50f;
				mTTS.setSpeechRate(rate);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});

		ivVolDown.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				int progress = sbVolume.getProgress();
				if (progress>10) progress=progress-10; else progress = 0;
				sbVolume.setProgress(progress);
				mCoolReader.setVolume(progress);
			}
		});
		ivVolUp.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				int progress = sbVolume.getProgress();
				if (progress<100) progress=progress+10; else progress = 100;
				sbVolume.setProgress(progress);
				mCoolReader.setVolume(progress);
			}
		});

		sbVolume.setOnSeekBarChangeListener( new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				mCoolReader.setVolume(progress);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		
		mPanel.requestFocus();
	}
	
}
