import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/userController.ts'
/*
* 登录用户信息
* */
export const useLoginUserStore = defineStore('loginUser', () => {
  // 默认值
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',

  })
  // 生成新的随机名字
  const generateRandomName = () => {
    const randomNum = String(Math.floor(Math.random() * 1000)).padStart(3, '0');
    return `官方ai水军${randomNum}号`;
  }
  // 获取当前会话的随机名字
  const getRandomName = () => {
    let randomName = sessionStorage.getItem('randomName');
    if (!randomName) {
      randomName = generateRandomName();
      sessionStorage.setItem('randomName', randomName);
    }
    return randomName;
  }
  // 获取登录用户信息
  async function fetchLoginUser() {
    const res = await getLoginUser()
    if (res.data.code === 0 && res.data.data) {

      if (res.data.code === 0 && res.data.data) {
        loginUser.value = res.data.data
      }
    }
  }
  // 更新登录用户信息
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser

  }

  return {
    loginUser,
    getRandomName, // 导出 randomName
    setLoginUser,
    fetchLoginUser }
})
