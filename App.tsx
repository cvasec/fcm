import React, { useCallback, useEffect, useState } from 'react';
import {
  FlatList,
  PermissionsAndroid,
  Platform,
  Pressable,
  Share,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { FcmTts, ReceivedMessage, TtsSettings } from './src/FcmTts';

const round1 = (n: number) => Math.round(n * 10) / 10;

function Button({
  title,
  onPress,
  disabled,
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        (pressed || disabled) && styles.buttonPressed,
      ]}
    >
      <Text style={styles.buttonText}>{title}</Text>
    </Pressable>
  );
}

function Stepper({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <View style={styles.row}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.stepper}>
        <Button title="−" onPress={() => onChange(round1(value - 0.1))} />
        <Text style={styles.stepperValue}>{value.toFixed(1)}</Text>
        <Button title="+" onPress={() => onChange(round1(value + 0.1))} />
      </View>
    </View>
  );
}

function App() {
  const [token, setToken] = useState<string | null>(null);
  const [tokenError, setTokenError] = useState<string | null>(null);
  const [settings, setSettings] = useState<TtsSettings | null>(null);
  const [history, setHistory] = useState<ReceivedMessage[]>([]);
  const [testText, setTestText] = useState(
    '안녕하세요. FCM 음성 알림 테스트입니다.',
  );

  const refreshHistory = useCallback(() => {
    FcmTts.getHistory().then(setHistory);
  }, []);

  useEffect(() => {
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
      );
    }
    FcmTts.getToken()
      .then(setToken)
      .catch((e: Error) => setTokenError(e.message));
    FcmTts.getSettings().then(setSettings);
    refreshHistory();

    const subs = [
      FcmTts.onMessage(message =>
        setHistory(prev => [message, ...prev].slice(0, 50)),
      ),
      FcmTts.onTokenRefresh(setToken),
    ];
    return () => subs.forEach(s => s.remove());
  }, [refreshHistory]);

  const updateSettings = (patch: Partial<TtsSettings>) => {
    FcmTts.setSettings(patch).then(setSettings);
  };

  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" />
      <SafeAreaView style={styles.container}>
        <FlatList
          data={history}
          keyExtractor={(item, index) => `${item.id}-${index}`}
          contentContainerStyle={styles.content}
          ListHeaderComponent={
            <>
              <Text style={styles.title}>FCM TTS</Text>

              <View style={styles.card}>
                <Text style={styles.cardTitle}>FCM 토큰</Text>
                {tokenError ? (
                  <Text style={styles.error}>{tokenError}</Text>
                ) : (
                  <Text selectable style={styles.token}>
                    {token ?? '불러오는 중…'}
                  </Text>
                )}
                {token && (
                  <Button
                    title="토큰 공유 / 복사"
                    onPress={() => Share.share({ message: token })}
                  />
                )}
              </View>

              {settings && (
                <View style={styles.card}>
                  <Text style={styles.cardTitle}>음성 설정</Text>
                  <View style={styles.row}>
                    <Text style={styles.label}>수신 메시지 읽기</Text>
                    <Switch
                      value={settings.enabled}
                      onValueChange={enabled => updateSettings({ enabled })}
                    />
                  </View>
                  <Stepper
                    label="속도"
                    value={settings.rate}
                    onChange={rate => updateSettings({ rate })}
                  />
                  <Stepper
                    label="높낮이"
                    value={settings.pitch}
                    onChange={pitch => updateSettings({ pitch })}
                  />
                  <View style={styles.row}>
                    <Text style={styles.label}>언어</Text>
                    <TextInput
                      style={styles.languageInput}
                      defaultValue={settings.language}
                      autoCapitalize="none"
                      onEndEditing={e =>
                        updateSettings({ language: e.nativeEvent.text.trim() })
                      }
                    />
                  </View>
                </View>
              )}

              <View style={styles.card}>
                <Text style={styles.cardTitle}>테스트</Text>
                <TextInput
                  style={styles.input}
                  value={testText}
                  onChangeText={setTestText}
                  multiline
                />
                <View style={styles.buttons}>
                  <Button
                    title="읽기"
                    disabled={!testText.trim()}
                    onPress={() => FcmTts.speak(testText)}
                  />
                  <Button title="정지" onPress={() => FcmTts.stop()} />
                </View>
              </View>

              <View style={[styles.row, styles.historyHeader]}>
                <Text style={styles.cardTitle}>수신 기록</Text>
                <Button
                  title="지우기"
                  onPress={() => FcmTts.clearHistory().then(refreshHistory)}
                />
              </View>
            </>
          }
          ListEmptyComponent={
            <Text style={styles.empty}>아직 받은 메시지가 없습니다.</Text>
          }
          renderItem={({ item }) => (
            <Pressable
              style={styles.historyItem}
              onPress={() => FcmTts.speak(item.text)}
            >
              {!!item.title && (
                <Text style={styles.historyTitle}>{item.title}</Text>
              )}
              <Text style={styles.historyText}>{item.text}</Text>
              <Text style={styles.historyTime}>
                {new Date(item.receivedAt).toLocaleString()}
              </Text>
            </Pressable>
          )}
        />
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f7' },
  content: { padding: 16, gap: 12 },
  title: { fontSize: 28, fontWeight: '700', color: '#111', marginBottom: 4 },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 16, gap: 10 },
  cardTitle: { fontSize: 16, fontWeight: '600', color: '#111' },
  token: { fontSize: 12, color: '#333', fontFamily: 'monospace' },
  error: { color: '#c62828' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  label: { fontSize: 15, color: '#333' },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  stepperValue: { width: 36, textAlign: 'center', fontSize: 15, color: '#111' },
  languageInput: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
    minWidth: 90,
    color: '#111',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 10,
    minHeight: 60,
    color: '#111',
    textAlignVertical: 'top',
  },
  buttons: { flexDirection: 'row', gap: 8 },
  button: {
    backgroundColor: '#1a73e8',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    alignItems: 'center',
  },
  buttonPressed: { opacity: 0.6 },
  buttonText: { color: '#fff', fontWeight: '600' },
  historyHeader: { marginTop: 8 },
  historyItem: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    gap: 4,
  },
  historyTitle: { fontWeight: '600', color: '#111' },
  historyText: { fontSize: 15, color: '#222' },
  historyTime: { fontSize: 12, color: '#888' },
  empty: { textAlign: 'center', color: '#888', marginTop: 16 },
});

export default App;
