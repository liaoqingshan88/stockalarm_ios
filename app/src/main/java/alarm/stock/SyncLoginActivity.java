package alarm.stock;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 多端同步：登录 / 注册 / 修改密码 界面。
 *
 * 说明：
 *  - 注册使用初始密码 123456，用户可后续修改
 *  - 登录成功后保存 token，回到主界面触发全量同步
 *  - 退出登录清空 token
 */
public class SyncLoginActivity extends Activity {

    private EditText editPhone;
    private EditText editPassword;
    private Button btnLogin;
    private Button btnRegister;
    private TextView btnChangePwd;
    private TextView textStatus;
    private Button btnLogout;
    private TextView textCurrentAccount;

    private SyncManager syncManager;
    private SyncClient client;

    // 当前账号信息展示
    private TextView textAccountInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_login);

        syncManager = new SyncManager(this);
        client = syncManager.getClient();

        editPhone = (EditText) findViewById(R.id.sync_edit_phone);
        editPassword = (EditText) findViewById(R.id.sync_edit_password);
        btnLogin = (Button) findViewById(R.id.sync_btn_login);
        btnRegister = (Button) findViewById(R.id.sync_btn_register);
        btnChangePwd = (TextView) findViewById(R.id.sync_btn_change_pwd);
        textStatus = (TextView) findViewById(R.id.sync_text_status);
        btnLogout = (Button) findViewById(R.id.sync_btn_logout);
        textAccountInfo = (TextView) findViewById(R.id.sync_text_account);
        textStatus.setMovementMethod(new android.text.method.ScrollingMovementMethod());

        // 显示当前登录状态
        refreshAccountUi();

        // 登录
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogin();
            }
        });

        // 注册（初始密码 123456）
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doRegister();
            }
        });

        // 修改密码（需已登录）
        btnChangePwd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doChangePassword();
            }
        });

        // 退出登录
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                client.logout();
                syncManager.stopPeriodicSync();
                Toast.makeText(SyncLoginActivity.this, "已退出登录", Toast.LENGTH_SHORT).show();
                refreshAccountUi();
                setResult(RESULT_OK);
            }
        });
    }

    private void refreshAccountUi() {
        if (client.isLoggedIn()) {
            textAccountInfo.setText("当前账号：" + client.getPhone() + "\n已开启多端同步");
            editPhone.setHint("");
            editPassword.setHint("");
            btnLogout.setVisibility(View.VISIBLE);
            btnChangePwd.setVisibility(View.VISIBLE);
        } else {
            textAccountInfo.setText("未登录\n登录后股票列表与报警配置将在多端同步");
            btnLogout.setVisibility(View.GONE);
            btnChangePwd.setVisibility(View.GONE);
        }
    }

    private void doLogin() {
        final String phone = editPhone.getText().toString().trim();
        final String pwd = editPassword.getText().toString();
        if (phone.length() != 11) {
            Toast.makeText(this, "请输入11位手机号", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pwd.length() == 0) {
            Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, "登录中...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String msg = client.login(phone, pwd);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false, "登录成功");
                            refreshAccountUi();
                            Toast.makeText(SyncLoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                            // 登录后触发全量同步（后台线程）
                            startSyncAfterLogin();
                        }
                    });
                } catch (final Exception e) {
                    Log.w("SyncLogin", "登录失败: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false, "");
                            Toast.makeText(SyncLoginActivity.this, "登录失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void startSyncAfterLogin() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    syncManager.syncOnLogin();
                    syncManager.startPeriodicSync();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textStatus.setText("✅ 同步成功，股票与报警配置已更新");
                        }
                    });
                } catch (final Exception e) {
                    Log.w("SyncLogin", "同步失败: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textStatus.setText("⚠️ 同步失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void doRegister() {
        final String phone = editPhone.getText().toString().trim();
        if (phone.length() != 11) {
            Toast.makeText(this, "请输入11位手机号", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, "注册中...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String msg = client.register(phone);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false, "");
                            refreshAccountUi();
                            Toast.makeText(SyncLoginActivity.this, "注册成功，初始密码 123456", Toast.LENGTH_LONG).show();
                            startSyncAfterLogin();
                        }
                    });
                } catch (final Exception e) {
                    Log.w("SyncLogin", "注册失败: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false, "");
                            Toast.makeText(SyncLoginActivity.this, "注册失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void doChangePassword() {
        if (!client.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        final String pwd = editPassword.getText().toString();
        if (pwd.length() < 6) {
            Toast.makeText(this, "请输入新密码（至少6位）", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, "修改中...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 需验证旧密码：用当前 token 调用即可（服务端要求传旧密码）
                    // 这里简化：通过登录旧密码验证。改为：直接改密码需旧密码，提示用户输入当前密码。
                    // 为简单，改密码流程：输入新密码到密码框，弹窗要求输旧密码。
                    // 由于界面只有一个密码框，这里用旧密码 = 输入的密码，新密码 = 输入的密码+后缀不现实。
                    // 改用对话框方式处理旧密码。
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setBusy(false, "");
                            promptOldPassword(pwd);
                        }
                    });
                } catch (Exception e) {
                    Log.w("SyncLogin", "change pwd error", e);
                }
            }
        }).start();
    }

    /**
     * 弹出旧密码输入框，确认后修改。
     */
    private void promptOldPassword(final String newPwd) {
        final EditText input = new EditText(this);
        input.setHint("请输入当前密码");
        new android.app.AlertDialog.Builder(this)
            .setTitle("修改密码")
            .setView(input)
            .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    final String oldPwd = input.getText().toString();
                    setBusy(true, "修改中...");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final String msg = client.changePassword(oldPwd, newPwd);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setBusy(false, "");
                                        refreshAccountUi();
                                        Toast.makeText(SyncLoginActivity.this, "修改成功，请用新密码重新登录", Toast.LENGTH_LONG).show();
                                    }
                                });
                            } catch (final Exception e) {
                                Log.w("SyncLogin", "改密失败: " + e.getMessage());
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setBusy(false, "");
                                        Toast.makeText(SyncLoginActivity.this, "修改失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }).start();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void setBusy(boolean busy, String status) {
        btnLogin.setEnabled(!busy);
        btnRegister.setEnabled(!busy);
        if (status != null && status.length() > 0) {
            textStatus.setText(status);
        }
    }
}
