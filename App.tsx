import { StatusBar } from 'expo-status-bar';
import { PermissionsAndroid, StyleSheet, Text, View } from 'react-native';
import TimelapseEngine from './modules/timelapse-engine/src/TimelapseEngineModule';

export default function App() {

  const pressButton = async () => {
    await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS);
    await TimelapseEngine.start({ host: '192.168.1.109', port: 8080, intervalMs: 5000, totalShots: 5 });
  }

  return (
    <View style={styles.container}>
      <Text>Open up App.tsx to start working on your app!</Text>
      <Text onPress={pressButton}>Start timelapse h</Text>
      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
