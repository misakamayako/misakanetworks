package per.misaka.misakanetworks.user

data class RegisterRequest(val email: String, val password: String)

data class RegisterResponse(val userId: Long, val bindToken: String, val otpauthUri: String)

data class BindMfaRequest(val bindToken: String, val code: String)

data class LoginRequest(val email: String, val password: String, val code: String? = null)

data class LoginResponse(val accessToken: String, val expiresIn: Long, val email: String)

data class ChangePasswordRequest(val oldPassword: String, val newPassword: String)

data class MessageResponse(val message: String)
