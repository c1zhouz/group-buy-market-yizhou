document.addEventListener("DOMContentLoaded", () => {
  const loginForm = document.getElementById("loginForm")
  const errorMessage = document.getElementById("errorMessage")
  const redirect = new URLSearchParams(window.location.search).get("redirect")

  loginForm.addEventListener("submit", (e) => {
    e.preventDefault()

    const username = document.getElementById("username").value
    const password = document.getElementById("password").value

    // 登录成功
    errorMessage.style.display = "none"

    // 设置Cookie (有效期为1天)
    const expirationDate = new Date()
    expirationDate.setDate(expirationDate.getDate() + 1)
    document.cookie = `username=${username}; expires=${expirationDate.toUTCString()}; path=/`

    // 默认跳转商城，支持通过 redirect 参数进入后台管理页
    window.location.href = redirect || "index.html"
  })
})

